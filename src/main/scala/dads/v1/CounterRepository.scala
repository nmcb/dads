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

  final val CounterColumn = "value"
  final val SourceColumn  = "source"
  final val RowTimeColumn = "rowtime"
  final val ColTimeColumn = "coltime"

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
          .increment(CounterColumn, literal(adjustment.value))
          .whereColumn(SourceColumn).isEqualTo(literal(adjustment.sourceId))
          .whereColumn(RowTimeColumn).isEqualTo(literal(counter.rowTime.toEpochMilli))
          .whereColumn(ColTimeColumn).isEqualTo(literal(counter.colTime.toEpochMilli))
          .build()
          .updateAsync()
      }

      def getFrom(counterOn: CounterOn)(sourceId: SourceId)(instant: Instant): Future[Long] = {
        val counter = counterOn(instant)

        selectFrom(settings.counterKeyspace, counter.tableName)
          .column(CounterColumn)
          .column(SourceColumn)
          .column(RowTimeColumn)
          .column(ColTimeColumn)
          .whereColumn(SourceColumn).isEqualTo(literal(sourceId))
          .whereColumn(RowTimeColumn).isEqualTo(literal(counter.rowTime.toEpochMilli))
          .whereColumn(ColTimeColumn).isEqualTo(literal(counter.colTime.toEpochMilli))
          .build()
          .selectOptionAsync()
          .map(toCounter)
      }

      private def toDone: Any => Done =
        _ => Done

      private def toCounter(rs: Option[Row]): Long =
        rs.map(_.getLong(CounterColumn)).getOrElse(0)
    }

  type CounterOn = Instant => CounterId

  case class CounterId( rowTime     : Instant             // Identifies a counter's major instant
                      , colTime     : Instant             // Identifies a counter's minor instant
                      , tableName   : String              // Identifies a counter's Cassandra table
                      , rowTimeUnit : Option[ChronoUnit]  // Identifies a counter's major time unit
                      , colTimeUnit : ChronoUnit          // Identifies a counter's minor time unit
                      )

  object CounterOn {

    final val HoursByDaysCounterTable   = "day"
    final val DaysByMonthCounterTable   = "month"
    final val MonthsByYearsCounterTable = "year"
    final val WeeksByYearsCounterTable  = "year_week"
    final val YearsCounterTable         = "forever"

    private def apply(timeUnit: ChronoUnit, byTimeUnit: ChronoUnit, table: String): CounterOn =
      instant =>
        CounterId( rowTime     = truncatedTo(byTimeUnit)(instant)
                 , colTime     = truncatedTo(timeUnit)(instant)
                 , tableName   = table
                 , rowTimeUnit = Some(byTimeUnit)
                 , colTimeUnit = timeUnit
                 )

    val HoursByDays: CounterOn =
      CounterOn(HOURS, DAYS, HoursByDaysCounterTable)

    val DaysByMonths: CounterOn =
      CounterOn(DAYS, MONTHS, DaysByMonthCounterTable)

    val MonthsByYears: CounterOn =
      CounterOn(MONTHS, YEARS, MonthsByYearsCounterTable)

    val WeeksByYears: CounterOn =
      CounterOn(WEEKS, YEARS, WeeksByYearsCounterTable)

    val Years: CounterOn =
      instant =>
        CounterId( rowTime     = Instant.EPOCH
                 , colTime     = truncatedTo(YEARS)(instant)
                 , tableName   = YearsCounterTable
                 , rowTimeUnit = None
                 , colTimeUnit = YEARS
                 )

    val All: Seq[CounterOn] =
      Seq(HoursByDays, DaysByMonths, MonthsByYears, WeeksByYears, Years)

    def truncatedTo(chronoUnit: ChronoUnit)(instant: Instant): Instant = {

      val firstDayOfWeek: TemporalAdjuster =
        temporal => RepositoryFirstDayOfWeek.adjustInto(temporal)

      chronoUnit match {
        case YEARS  =>
          instant.atOffset(RepositoryTimeZoneOffset).`with`(firstDayOfYear).toInstant
        case MONTHS =>
          instant.atOffset(RepositoryTimeZoneOffset).`with`(firstDayOfMonth).toInstant
        case WEEKS  =>
          instant.atOffset(RepositoryTimeZoneOffset).`with`(firstDayOfWeek).toInstant
        case _  =>
          instant.truncatedTo(chronoUnit)
      }
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

case class Adjustment(sourceId: SourceId, instant: Instant, value: Long)

trait CounterRepository {

  def addTo(counterOn: CounterOn)(adjustment: Adjustment): Future[Done]

  def addToAll(adjustment: Adjustment): Future[Done]

  def getFrom(counter: CounterOn)(sourceId: SourceId)(instant: Instant): Future[Long]
}


