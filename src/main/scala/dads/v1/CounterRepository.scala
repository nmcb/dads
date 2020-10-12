/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1

import java.time._

import scala.concurrent._

import akka._
import akka.actor.typed._
import akka.stream.alpakka.cassandra._
import akka.stream.alpakka.cassandra.scaladsl._

import com.datastax.oss.driver.api.core.cql._
import com.datastax.oss.driver.api.querybuilder._
import com.datastax.oss.driver.api.querybuilder.term._

object CounterRepository {

  import temporal._

  import QueryBuilder._
  import ChronoUnit._
  import TemporalAdjusters._

  import DadsSettings._

  final val CounterValueColumn   = "value"
  final val SourceIdColumn       = "source"
  final val MajorInstantIdColumn = "rowtime"
  final val MinorInstantIdColumn = "coltime"

  sealed abstract class Bucket(val tableName: String)
  case object HourByDay   extends Bucket("day")
  case object DayByMonth  extends Bucket("month")
  case object MonthByYear extends Bucket("year")
  case object WeekByYear  extends Bucket("year_week")
  case object Year        extends Bucket("forever")


  def apply(settings: RepositorySettings)(implicit system: ActorSystem[_]): CounterRepository =
    new CounterRepository {

      import akka.actor.typed.scaladsl.adapter._

      implicit val executionContext: ExecutionContext =
        system.toClassic.dispatcher

      // FIXME since we use authentication we get warnings in test
      // [WARN] [s0] /127.0.0.1:9042 did not send an authentication challenge; This is suspicious because the driver expects authentication
      implicit val session: CassandraSession =
        CassandraSessionRegistry
          .get(system)
          .sessionFor(CassandraSessionSettings())

      def addTo(counterOn: CounterOn)(adjustment: Adjustment): Future[Done] = {
        val counter = counterOn(adjustment.instant)

        update(settings.counterKeyspace, counter.bucket.tableName)
          .increment(CounterValueColumn, literal(adjustment.value.toLong))
          .whereColumn(SourceIdColumn).isEqualTo(literal(adjustment.sourceId.uuid))
          .whereColumn(MajorInstantIdColumn).isEqualTo(literal(counter.majorInstant.toEpochMilli))
          .whereColumn(MinorInstantIdColumn).isEqualTo(literal(counter.minorInstant.toEpochMilli))
          .build()
          .updateAsync()
      }

      def getFrom(counterOn: CounterOn)(sourceId: SourceId)(instant: Instant): Future[Long] = {
        val counter = counterOn(instant)

        selectFrom(settings.counterKeyspace, counter.bucket.tableName)
          .column(CounterValueColumn)
          .column(SourceIdColumn)
          .column(MajorInstantIdColumn)
          .column(MinorInstantIdColumn)
          .whereColumn(SourceIdColumn).isEqualTo(literal(sourceId.uuid))
          .whereColumn(MajorInstantIdColumn).isEqualTo(literal(counter.majorInstant.toEpochMilli))
          .whereColumn(MinorInstantIdColumn).isEqualTo(literal(counter.minorInstant.toEpochMilli))
          .build()
          .selectOptionAsync()
          .map(toCounter)
      }

      override def getFrom(spanOn: CounterSpanOn)(sourceId: SourceId)(instant: Instant): Future[Seq[Long]] = {

        import scala.jdk.CollectionConverters._

        val counters = spanOn(instant)

        val minorInstants: java.util.List[Term] =
          counters.map(_.majorInstant.toEpochMilli).map(literal).asInstanceOf[Seq[Term]].asJava

        val majorInstants: java.util.List[Term] =
          counters.map(_.minorInstant.toEpochMilli).map(literal).asInstanceOf[Seq[Term]].asJava

        selectFrom(settings.counterKeyspace, spanOn.bucket.tableName)
          .column(CounterValueColumn)
          .column(SourceIdColumn)
          .column(MajorInstantIdColumn)
          .column(MinorInstantIdColumn)
          .whereColumn(SourceIdColumn).isEqualTo(literal(sourceId.uuid))
          .whereColumn(MajorInstantIdColumn).in(majorInstants)
          .whereColumn(MinorInstantIdColumn).in(minorInstants)
          .build()
          .selectSeqAsync()
          .map(toCounters)
      }

      private def toCounter(rs: Option[Row]): Long =
        rs.map(_.getLong(CounterValueColumn)).getOrElse(0)

      private def toCounters(rs: Seq[Row]): Seq[Long] =
        rs.map(_.getLong(CounterValueColumn))
    }

  case class Adjustment( sourceId : SourceId
                       , instant  : Instant
                       , value    : BigDecimal
                       ) {
//     FIXME enable, currently triggers under certain conditions
//     require(value >= 0, "value must be positive")
  }

  case class Counter( majorInstant    : Instant
                    , minorInstant    : Instant
                    , majorChronoUnit : ChronoUnit
                    , minorChronoUnit : ChronoUnit
                    , bucket          : Bucket
                    ) {

    import Counter._

    lazy val prevMinorInstant: Instant =
      truncatedTo(minorChronoUnit)(minorInstant.minus(RealTimeChronoUnit.getDuration))
  }

  object Counter {

    def truncatedTo(chronoUnit: ChronoUnit)(instant: Instant): Instant =
      chronoUnit match {
        case FOREVER => Instant.EPOCH
        case YEARS   => withRepositoryOffsetTruncatedToDays(firstDayOfYear)(instant)
        case MONTHS  => withRepositoryOffsetTruncatedToDays(firstDayOfMonth)(instant)
        case WEEKS   => withRepositoryOffsetTruncatedToDays(firstDayOfRepositoryWeek)(instant)
        case _       => instant.truncatedTo(chronoUnit)
      }

    private[this] val firstDayOfRepositoryWeek: TemporalAdjuster =
      temporal => FirstDayOfRepositoryWeek.adjustInto(temporal)

    private[this] def withRepositoryOffsetTruncatedToDays(adjuster: TemporalAdjuster)(instant: Instant): Instant =
      instant.atZone(TimeZoneOfRepositoryOffset).truncatedTo(DAYS).`with`(adjuster).toInstant

    /** Descending towards the past by `Counter.minorInstant`. */
    implicit val descendingCounterOrder: Ordering[Counter] =
      (x: Counter, y: Counter) => y.minorInstant.compareTo(x.minorInstant)
  }

  type CounterOn = Instant => Counter

  object CounterOn {

    import Counter._

    def apply(chronoUnit: ChronoUnit, byChronoUnit: ChronoUnit)(bucket: Bucket): CounterOn =
      instant => Counter( majorInstant    = truncatedTo(byChronoUnit)(instant)
                        , minorInstant    = truncatedTo(chronoUnit)(instant)
                        , majorChronoUnit = byChronoUnit
                        , minorChronoUnit = chronoUnit
                        , bucket          = bucket
                        )

    val HourByDayCounterOn: CounterOn =
      CounterOn(HOURS, DAYS)(HourByDay)

    val DayByMonthCounterOn: CounterOn =
      CounterOn(DAYS, MONTHS)(DayByMonth)

    val MonthByYearCounterOn: CounterOn =
      CounterOn(MONTHS, YEARS)(MonthByYear)

    val WeekByYearCounterOn: CounterOn =
      CounterOn(WEEKS, YEARS)(WeekByYear)

    val YearCounterOn: CounterOn =
      instant => Counter( majorInstant    = Instant.EPOCH
                        , minorInstant    = truncatedTo(YEARS)(instant)
                        , majorChronoUnit = FOREVER
                        , minorChronoUnit = YEARS
                        , bucket          = Year
                        )
  }

  case class CounterSpanOn(bucket: Bucket, underlying: Instant => Seq[Counter])
    extends (Instant => Seq[Counter]) {

    def apply(instant: Instant): Seq[Counter] =
      underlying(instant)
  }

  object CounterSpanOn {

    def apply(chronoUnit: ChronoUnit, byChronoUnit: ChronoUnit)(bucket: Bucket)(size: Int): CounterSpanOn =
      CounterSpanOn(bucket, start => unroll(size, start, CounterOn(chronoUnit, byChronoUnit)(bucket)))

    private def unroll(size: Int, start: Instant, counterOn: CounterOn) = {
      require(size > 0, "size must be a positive integer")

      @scala.annotation.tailrec
      def loop(before: Instant, accumulator: Vector[Counter]): Seq[Counter] =
        if (accumulator.length >= size)
          accumulator
        else {
          val prevCounter = counterOn(before)
          loop(prevCounter.prevMinorInstant, accumulator :+ prevCounter)
        }

      val firstCounter = counterOn(start)
      loop(firstCounter.prevMinorInstant, Vector(firstCounter))
    }

    val HourByDaySpanOf: Int => CounterSpanOn =
      size => CounterSpanOn(HOURS, DAYS)(HourByDay)(size)

    val DayByMonthSpanOf: Int => CounterSpanOn =
      size => CounterSpanOn(DAYS, MONTHS)(DayByMonth)(size)

    val MonthByYearSpanOf: Int => CounterSpanOn =
      size => CounterSpanOn(MONTHS, YEARS)(MonthByYear)(size)

    val WeekByYearSpanOf: Int => CounterSpanOn =
      size => CounterSpanOn(WEEKS, YEARS)(WeekByYear)(size)

    val YearSpanOf: Int => CounterSpanOn =
      size => CounterSpanOn(YEARS, FOREVER)(Year)(size)
  }
}

import CounterRepository._

trait CounterRepository extends Repository {

  def addTo(counterOn: CounterOn)(adjustment: Adjustment): Future[Done]

  def getFrom(counterOn: CounterOn)(sourceId: SourceId)(instant: Instant): Future[Long]

  def getFrom(spanOn: CounterSpanOn)(sourceId: SourceId)(instant: Instant): Future[Seq[Long]]
}
