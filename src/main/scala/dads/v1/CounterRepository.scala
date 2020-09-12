/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1

import java.time._
import java.util._

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

  final val TimeZoneOffset: java.time.ZoneOffset = java.time.ZoneOffset.UTC

  final val FirstDayOfWeek: java.time.DayOfWeek  = DayOfWeek.MONDAY

  case class Measurement(  sourceId     : UUID
                         , instant    : Instant
                         , adjustment : Long
                        )

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

      def addToAll(measurement: Measurement): Future[Done] =
        Future.sequence(All.map(counterOn => addTo(counterOn)(measurement))).map(toDone)

      def addTo(counterOn: CounterOn)(measurement: Measurement): Future[Done] = {
        val counter = counterOn(measurement.instant)

        update(settings.counterKeyspace, counter.tableName)
          .increment(CounterColumn, literal(measurement.adjustment))
          .whereColumn(SourceColumn).isEqualTo(literal(measurement.sourceId))
          .whereColumn(RowTimeColumn).isEqualTo(literal(counter.rowTime.toEpochMilli))
          .whereColumn(ColTimeColumn).isEqualTo(literal(counter.colTime.toEpochMilli))
          .build()
          .updateAsync
      }

      def getFrom(counterOn: CounterOn)(sourceId: UUID)(instant: Instant): Future[Long] = {
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
          .selectOptionAsync
          .map(toCounter)
      }

      private def toDone: Any => Done =
        _ => Done

      private def toCounter(rs: Option[Row]): Long =
        rs.map(_.getLong(CounterColumn)).getOrElse(0)
  }

  // MODEL

  trait CounterInstance {
    def rowTime   : Instant
    def colTime   : Instant
    def tableName : String
  }

  type CounterOn = Instant => CounterInstance

  private case class
    CounterFor( rowTime   : Instant
              , colTime   : Instant
              , tableName : String
              ) extends CounterInstance

  object CounterOn {

    final val DayCounterTable       = "day"
    final val MonthCounterTable     = "month"
    final val MonthYearCounterTable = "year"
    final val WeekYearCounterTable  = "year_week"
    final val AlwaysCounterTable    = "forever"

    private def apply(rowTimeUnit: ChronoUnit, colTimeUnit: ChronoUnit, table: String): CounterOn =
      instant =>
        CounterFor( rowTime   = truncatedTo(rowTimeUnit)(instant)
                  , colTime   = truncatedTo(colTimeUnit)(instant)
                  , tableName = table
                  )

    val Days: CounterOn =
      CounterOn(DAYS, HOURS, DayCounterTable)

    val Months: CounterOn =
      CounterOn(MONTHS, DAYS, MonthCounterTable)

    val MonthYears: CounterOn =
      CounterOn(YEARS, MONTHS, MonthYearCounterTable)

    val WeekYears: CounterOn =
      CounterOn(YEARS, WEEKS, WeekYearCounterTable)

    val SinceEpoch: CounterOn =
      instant =>
        CounterFor( rowTime   = Instant.EPOCH
                  , colTime   = truncatedTo(YEARS)(instant)
                  , tableName = AlwaysCounterTable
                  )

    val All: Seq[CounterOn] =
      Seq(Days, Months, MonthYears, WeekYears, SinceEpoch)

    // LOGIC

    def truncatedTo(chronoUnit: ChronoUnit)(instant: Instant): Instant = {

      def delegate(chronoUnit: ChronoUnit, localDateTime: LocalDateTime): LocalDateTime =
        chronoUnit match {
          case YEARS  =>
            localDateTime.truncatedTo(DAYS).`with`(firstDayOfYear)
          case MONTHS =>
            localDateTime.truncatedTo(DAYS).`with`(firstDayOfMonth)
          case WEEKS  =>
            val localDate = localDateTime.truncatedTo(DAYS)
            localDate.minusDays(localDate.getDayOfWeek.getValue - FirstDayOfWeek.getValue)
          case _  =>
            localDateTime.truncatedTo(chronoUnit)
        }

      val shadow   = LocalDateTime.ofInstant(instant, TimeZoneOffset)
      val shadowed = delegate(chronoUnit, shadow)
      shadowed.toInstant(TimeZoneOffset)
    }
  }

  // UTILS

  implicit class StatementUtil(statement: Statement[_])(implicit session: CassandraSession, system: ActorSystem[_]) {

    def selectOptionAsync: Future[Option[Row]] =
      session.select(statement).runWith(Sink.headOption)

    def selectAsync: Future[Seq[Row]] =
      session.select(statement).runWith(Sink.seq)

    def updateAsync: Future[Done] =
      session.executeWrite(statement)
  }
}

import CounterRepository._

trait CounterRepository {

  def addTo(counterOn: CounterOn)(measurement: Measurement): Future[Done]

  def addToAll(measurement: Measurement): Future[Done]

  def getFrom(counter: CounterOn)(sourceId: UUID)(instant: Instant): Future[Long]
}


