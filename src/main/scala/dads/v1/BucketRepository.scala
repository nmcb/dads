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

trait BucketRepository {

  import BucketRepository._

  def addTo(bucketFor: BucketFor)(measurement: Measurement): Future[Done]

  def addToAll(measurement: Measurement): Future[Done]

  def getFrom(bucket: BucketFor)(sourceId: UUID): Future[Long]
}

object BucketRepository {

  import temporal._

  import QueryBuilder._
  import BucketFor._
  import ChronoUnit._
  import TemporalAdjusters._

  val TimeZoneOffset: java.time.ZoneOffset = java.time.ZoneOffset.UTC

  case class Measurement( sourceId : UUID
                        , instant  : Instant
                        , value    : Long
                        )

  val ValueColumn   = "value"
  val SourceColumn  = "source"
  val RowTimeColumn = "rowtime"
  val ColTimeColumn = "coltime"

  def apply(settings: DadsSettings)(implicit system: ActorSystem[_]): BucketRepository =
    new BucketRepository {

      import akka.actor.typed.scaladsl.adapter._

      implicit val executionContext: ExecutionContext =
        system.toClassic.dispatcher

      implicit val session: CassandraSession =
        CassandraSessionRegistry
          .get(system)
          .sessionFor(CassandraSessionSettings())

      def addTo(bucketFor: BucketFor)(measurement: Measurement): Future[Done] =
        update(settings.bucketKeyspace, bucketFor.tableName)
          .increment(ValueColumn, literal(measurement.value))
          .whereColumn(SourceColumn).isEqualTo(literal(measurement.sourceId))
          .whereColumn(RowTimeColumn).isEqualTo(literal(bucketFor.rowTime.toEpochMilli))
          .whereColumn(ColTimeColumn).isEqualTo(literal(bucketFor.colTime.toEpochMilli))
          .build()
          .updateAsync

      override def addToAll(measurement: Measurement): Future[Done] =
        Future.sequence(
          Seq(Day, Month, Year, WeekYear, Forever)
            .map(bucket => bucket(measurement.instant))
            .map(addTo(_)(measurement))
        ).map(toDone)

      override def getFrom(bucket: BucketFor)(sourceId: UUID): Future[Long] =
        selectFrom(settings.bucketKeyspace, bucket.tableName)
          .column(ValueColumn)
          .whereColumn(SourceColumn).isEqualTo(literal(sourceId))
          .whereColumn(RowTimeColumn).isEqualTo(literal(bucket.rowTime.toEpochMilli))
          .whereColumn(ColTimeColumn).isEqualTo(literal(bucket.colTime.toEpochMilli))
          .build()
          .selectAsync
          .map(toOneValue)

      private def toDone: Any => Done =
        _ => Done

      private def toOneValue(rs: Seq[Row]): Long =
        rs.headOption.getOrElse(throw new RuntimeException("Boom")).getLong(ValueColumn)
  }

  // Model

  case class
    BucketFor( instant   : Instant
             , rowTime   : Instant
             , colTime   : Instant
             , tableName : String
             )

  type BucketOn = Instant => BucketFor

  object BucketFor {

    val DayHourBucketTable   = "day"
    val MonthDayBucketTable  = "month"
    val YearMonthBucketTable = "year"
    val YearWeekBucketTable  = "year_week"
    val ForeverBucketTable   = "forever"

    private def apply(rowTimeUnit: ChronoUnit, colTimeUnit: ChronoUnit, table: String): BucketOn =
      instant => new BucketFor(
          instant = instant
        , rowTime = truncatedTo(rowTimeUnit)(instant)
        , colTime = truncatedTo(colTimeUnit)(instant)
        , tableName = table
      )

    def Day: BucketOn =
      BucketFor(DAYS, HOURS, DayHourBucketTable)

    def Month: BucketOn =
      BucketFor(MONTHS, DAYS, MonthDayBucketTable)

    def Year: BucketOn =
      BucketFor(YEARS, MONTHS, YearMonthBucketTable)

    def WeekYear: BucketOn =
      BucketFor(YEARS, WEEKS, YearWeekBucketTable)

    def Forever: BucketOn =
      instant =>
        new BucketFor(
            instant   = instant
          , rowTime   = Instant.EPOCH
          , colTime   = truncatedTo(YEARS)(instant)
          , tableName = ForeverBucketTable
        )

    def truncatedTo(chronoUnit: ChronoUnit)(instant: Instant): Instant = {

      def shadow(chronoUnit: ChronoUnit)(localDateTime: LocalDateTime): LocalDateTime =
        chronoUnit match {
          case YEARS  =>
            localDateTime.truncatedTo(DAYS).`with`(firstDayOfYear())
          case MONTHS =>
            localDateTime.truncatedTo(DAYS).`with`(firstDayOfMonth())
          case WEEKS  =>
            val today = localDateTime.truncatedTo(DAYS)
            today.minusDays(today.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue())
          case _  =>
            localDateTime.truncatedTo(chronoUnit)
        }

      shadow(chronoUnit)(LocalDateTime.ofInstant(instant, TimeZoneOffset)).toInstant(TimeZoneOffset)
    }
  }

  // Utils

  implicit class StatementUtil(statement: Statement[_])(implicit session: CassandraSession, system: ActorSystem[_]) {

    def selectAsync: Future[Seq[Row]] =
      session.select(statement).runWith(Sink.seq)

    def updateAsync: Future[Done] =
      session.executeWrite(statement)
  }
}
