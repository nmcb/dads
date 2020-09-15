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

  final val RepositoryTimeZoneOffset: java.time.ZoneOffset = java.time.ZoneOffset.UTC

  final val RepositoryFirstDayOfWeek: java.time.DayOfWeek  = java.time.DayOfWeek.MONDAY

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

  case class CounterId( majorInstant  : Instant
                      , minorInstant  : Instant
                      , tableName     : String
                      , majorTimeUnit : ChronoUnit
                      , minorTimeUnit : ChronoUnit
                      ) {

    private lazy val nextMajorInstant: Instant =
      majorInstant.plus(1, majorTimeUnit)

    private lazy val nextMinorInstant: Instant =
      minorInstant.plus(1, minorTimeUnit)

    def nextMajor: CounterId =
      copy(majorInstant = nextMajorInstant, minorInstant = nextMajorInstant)

    def nextMinor: CounterId =
      if (nextMinorInstant.isBefore(nextMajorInstant))
        copy(minorInstant = nextMinorInstant)
      else
        copy(majorInstant = nextMajorInstant, minorInstant = nextMinorInstant)

    private lazy val prevMajorInstant: Instant =
      majorInstant.minus(1, majorTimeUnit)

    private lazy val prevMinorInstant: Instant =
      minorInstant.minus(1, minorTimeUnit)

    def prevMajor: CounterId =
      copy(majorInstant = prevMajorInstant, minorInstant = prevMajorInstant)

    def prevMinor: CounterId =
      if (prevMinorInstant.isBefore(majorInstant))
        copy(majorInstant = prevMajorInstant, minorInstant = prevMinorInstant)
      else
        copy(minorInstant = prevMinorInstant)
  }

  object CounterId {

    val withRepositoryOffsetTruncatedTo: TemporalAdjuster => Instant => Instant =
      adjuster => instant => instant.atZone(RepositoryTimeZoneOffset).`with`(adjuster).toInstant

    val firstDayOfRepositoryWeek: TemporalAdjuster =
      temporal => RepositoryFirstDayOfWeek.adjustInto(temporal)

    def truncatedTo(chronoUnit: ChronoUnit)(instant: Instant): Instant = {

      chronoUnit match {
        case YEARS  =>
          withRepositoryOffsetTruncatedTo(firstDayOfYear)(instant)
        case MONTHS =>
          withRepositoryOffsetTruncatedTo(firstDayOfMonth)(instant)
        case WEEKS  =>
          withRepositoryOffsetTruncatedTo(firstDayOfRepositoryWeek)(instant)
        case _  =>
          instant.truncatedTo(chronoUnit)
      }
    }
  }

  type CounterOn = Instant => CounterId

  object CounterOn {

    import CounterId._

    final val HoursByDaysCounterTable   = "day"
    final val DaysByMonthsCounterTable  = "month"
    final val MonthsByYearsCounterTable = "year"
    final val WeeksByYearsCounterTable  = "year_week"
    final val YearsCounterTable         = "forever"

    private def apply(timeUnit: ChronoUnit, byTimeUnit: ChronoUnit, tableName: String): CounterOn =
      instant =>
        CounterId( majorInstant  = truncatedTo(byTimeUnit)(instant)
                 , minorInstant  = truncatedTo(timeUnit)(instant)
                 , tableName     = tableName
                 , majorTimeUnit = byTimeUnit
                 , minorTimeUnit = timeUnit
                 )

    val HoursByDays: CounterOn =
      CounterOn(HOURS, DAYS, HoursByDaysCounterTable)

    val DaysByMonths: CounterOn =
      CounterOn(DAYS, MONTHS, DaysByMonthsCounterTable)

    val MonthsByYears: CounterOn =
      CounterOn(MONTHS, YEARS, MonthsByYearsCounterTable)

    val WeeksByYears: CounterOn =
      CounterOn(WEEKS, YEARS, WeeksByYearsCounterTable)

    val Years: CounterOn =
      instant =>
        CounterId( majorInstant  = Instant.EPOCH
                 , minorInstant  = truncatedTo(YEARS)(instant)
                 , tableName     = YearsCounterTable
                 , majorTimeUnit = FOREVER
                 , minorTimeUnit = YEARS
                 )

    val All: Seq[CounterOn] =
      Seq(HoursByDays, DaysByMonths, MonthsByYears, WeeksByYears, Years)
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

case class Adjustment(sourceId: SourceId, instant: Instant, value: Long)

trait CounterRepository {

  def addTo(counterOn: CounterOn)(adjustment: Adjustment): Future[Done]

  def addToAll(adjustment: Adjustment): Future[Done]

  def getFrom(counter: CounterOn)(sourceId: SourceId)(instant: Instant): Future[Long]
}


