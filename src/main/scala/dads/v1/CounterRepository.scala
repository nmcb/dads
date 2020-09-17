/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1

import java.time._

import scala.concurrent._

import akka._
import akka.actor.typed._
import akka.stream.scaladsl._
import akka.stream.alpakka.cassandra._
import akka.stream.alpakka.cassandra.scaladsl._

import com.datastax.oss.driver.api.core.cql._
import com.datastax.oss.driver.api.querybuilder._

object CounterRepository {

  import temporal._

  import QueryBuilder._
  import CounterOn._
  import ChronoUnit._
  import TemporalAdjusters._

  final val TimeZoneOfRepositoryOffset: java.time.ZoneOffset = java.time.ZoneOffset.UTC
  final val FirstDayOfRepositoryWeek  : java.time.DayOfWeek  = java.time.DayOfWeek.MONDAY

  final val CounterValueColumn   = "value"
  final val SourceIdColumn       = "source"
  final val MajorInstantIdColumn = "rowtime"
  final val MinorInstantIdColumn = "coltime"

  def apply(settings: DadsSettings)(implicit system: ActorSystem[_]): CounterRepository =
    new CounterRepository {

      import akka.actor.typed.scaladsl.adapter._

      implicit val executionContext: ExecutionContext =
        system.toClassic.dispatcher

      implicit val session: CassandraSession =
        CassandraSessionRegistry
          .get(system)
          .sessionFor(CassandraSessionSettings())

      def addToAll(adjustment: Adjustment): Future[Done] =
        Future.sequence(All.map(counterOn => addTo(counterOn)(adjustment))).map(toDone)

      def addTo(counterOn: CounterOn)(adjustment: Adjustment): Future[Done] = {
        val counter = counterOn(adjustment.instant)

        update(settings.counterKeyspace, counter.tableName)
          .increment(CounterValueColumn, literal(adjustment.value))
          .whereColumn(SourceIdColumn).isEqualTo(literal(adjustment.sourceId))
          .whereColumn(MajorInstantIdColumn).isEqualTo(literal(counter.majorInstant.toEpochMilli))
          .whereColumn(MinorInstantIdColumn).isEqualTo(literal(counter.minorInstant.toEpochMilli))
          .build()
          .updateAsync()
      }

      def getFrom(counterOn: CounterOn)(sourceId: SourceId)(instant: Instant): Future[Long] = {
        val counter = counterOn(instant)

        selectFrom(settings.counterKeyspace, counter.tableName)
          .column(CounterValueColumn)
          .column(SourceIdColumn)
          .column(MajorInstantIdColumn)
          .column(MinorInstantIdColumn)
          .whereColumn(SourceIdColumn).isEqualTo(literal(sourceId))
          .whereColumn(MajorInstantIdColumn).isEqualTo(literal(counter.majorInstant.toEpochMilli))
          .whereColumn(MinorInstantIdColumn).isEqualTo(literal(counter.minorInstant.toEpochMilli))
          .build()
          .selectOptionAsync()
          .map(toCounter)
      }

      private def toDone: Any => Done =
        _ => Done

      private def toCounter(rs: Option[Row]): Long =
        rs.map(_.getLong(CounterValueColumn)).getOrElse(0)
    }

  case class Adjustment( sourceId : SourceId
                       , instant  : Instant
                       , value    : Long
                       )

  case class CounterId( majorInstant    : Instant
                      , minorInstant    : Instant
                      , tableName       : String
                      , majorChronoUnit : ChronoUnit
                      , minorChronoUnit : ChronoUnit
                      ) {

    lazy val sampleBefore: Instant =
      minorInstant
        .minusMillis(
          minorChronoUnit
            .getDuration
            .dividedBy(CounterId.SampleFactor)
            .toMillis)
  }

  object CounterId {

    val SampleFactor = 3

    def truncatedTo(chronoUnit: ChronoUnit)(instant: Instant): Instant =
      chronoUnit match {
        case YEARS  => withRepositoryOffsetTruncatedToDays(firstDayOfYear)(instant)
        case MONTHS => withRepositoryOffsetTruncatedToDays(firstDayOfMonth)(instant)
        case WEEKS  => withRepositoryOffsetTruncatedToDays(firstDayOfRepositoryWeek)(instant)
        case _      => instant.truncatedTo(chronoUnit)
      }

    private[this] val firstDayOfRepositoryWeek: TemporalAdjuster =
      temporal => FirstDayOfRepositoryWeek.adjustInto(temporal)

    private[this] def withRepositoryOffsetTruncatedToDays(adjuster: TemporalAdjuster)(instant: Instant): Instant =
      instant.atZone(TimeZoneOfRepositoryOffset).truncatedTo(DAYS).`with`(adjuster).toInstant

    implicit val counterIdOrdering: Ordering[CounterId] =
      (x: CounterId, y: CounterId) => x.minorInstant.compareTo(y.minorInstant)
  }

  type CounterOn = Instant => CounterId

  object CounterOn {

    import CounterId._

    final val HoursByDayCounterTable   = "day"
    final val DaysByMonthCounterTable  = "month"
    final val MonthsByYearCounterTable = "year"
    final val WeeksByYearCounterTable  = "year_week"
    final val YearsCounterTable        = "forever"

    private def apply(chronoUnit: ChronoUnit, byChronoUnit: ChronoUnit, tableName: String): CounterOn =
      instant =>
        CounterId( majorInstant    = truncatedTo(byChronoUnit)(instant)
                 , minorInstant    = truncatedTo(chronoUnit)(instant)
                 , tableName       = tableName
                 , majorChronoUnit = byChronoUnit
                 , minorChronoUnit = chronoUnit
                 )

    val HoursByDay: CounterOn =
      CounterOn(HOURS, DAYS, HoursByDayCounterTable)

    val DaysByMonth: CounterOn =
      CounterOn(DAYS, MONTHS, DaysByMonthCounterTable)

    val MonthsByYear: CounterOn =
      CounterOn(MONTHS, YEARS, MonthsByYearCounterTable)

    val WeeksByYear: CounterOn =
      CounterOn(WEEKS, YEARS, WeeksByYearCounterTable)

    val Years: CounterOn =
      instant =>
        CounterId( majorInstant    = Instant.EPOCH
                 , minorInstant    = truncatedTo(YEARS)(instant)
                 , tableName       = YearsCounterTable
                 , majorChronoUnit = FOREVER
                 , minorChronoUnit = YEARS
                 )

    val All: Seq[CounterOn] =
      Seq(HoursByDay, DaysByMonth, MonthsByYear, WeeksByYear, Years)
  }

  type CounterSpanOn = Instant => Seq[CounterId]

  object CounterSpanOn {

    def apply(counterOn: CounterOn)(size: Int): CounterSpanOn =
      start => {

        def unroll(before: Instant, accumulator: Vector[CounterId]): Seq[CounterId] =
          if (accumulator.length >= size)
            accumulator
          else {
            val prevCounterId = counterOn(before)
            unroll(prevCounterId.sampleBefore, prevCounterId +: accumulator)
          }

        require(size > 0, "size must be a positive integer")
        val firstCounterId = counterOn(start)
        unroll(firstCounterId.sampleBefore, Vector(firstCounterId))
      }
  }

  implicit class StatementUtil(statement: Statement[_])(implicit session: CassandraSession, system: ActorSystem[_]) {

    def selectOptionAsync(): Future[Option[Row]] =
      session.select(statement).runWith(Sink.headOption)

    def selectAsync(): Future[Seq[Row]] =
      session.select(statement).runWith(Sink.seq)

    def updateAsync(): Future[Done] =
      session.executeWrite(statement)
  }
}

import CounterRepository._

trait CounterRepository {

  def addTo(counterOn: CounterOn)(adjustment: Adjustment): Future[Done]

  def addToAll(adjustment: Adjustment): Future[Done]

  def getFrom(counter: CounterOn)(sourceId: SourceId)(instant: Instant): Future[Long]
}


