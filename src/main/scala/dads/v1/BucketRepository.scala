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

  def addTo(bucketOn: BucketOn)(measurement: Measurement): Future[Done]

  def addToAll(measurement: Measurement): Future[Done]

  def getFrom(bucket: BucketOn)(sourceId: UUID)(instant: Instant): Future[Long]
}

object BucketRepository {

  import temporal._

  import QueryBuilder._
  import BucketOn._
  import ChronoUnit._
  import TemporalAdjusters._

  val TimeZoneOffset: java.time.ZoneOffset = java.time.ZoneOffset.UTC

  val FirstDayOfWeek: java.time.DayOfWeek  = DayOfWeek.MONDAY

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

      def addTo(bucketOn: BucketOn)(measurement: Measurement): Future[Done] =
        update(settings.bucketKeyspace, bucketOn(measurement.instant).tableName)
          .increment(ValueColumn, literal(measurement.value))
          .whereColumn(SourceColumn).isEqualTo(literal(measurement.sourceId))
          .whereColumn(RowTimeColumn).isEqualTo(literal(bucketOn(measurement.instant).rowTime.toEpochMilli))
          .whereColumn(ColTimeColumn).isEqualTo(literal(bucketOn(measurement.instant).colTime.toEpochMilli))
          .build()
          .updateAsync

      override def addToAll(measurement: Measurement): Future[Done] =
        Future.sequence(All.map(bucketOn => addTo(bucketOn)(measurement))).map(toDone)

      override def getFrom(bucketOn: BucketOn)(sourceId: UUID)(instant: Instant): Future[Long] =
        selectFrom(settings.bucketKeyspace, bucketOn(instant).tableName)
          .column(ValueColumn)
          .whereColumn(SourceColumn).isEqualTo(literal(sourceId))
          .whereColumn(RowTimeColumn).isEqualTo(literal(bucketOn(instant).rowTime.toEpochMilli))
          .whereColumn(ColTimeColumn).isEqualTo(literal(bucketOn(instant).colTime.toEpochMilli))
          .build()
          .selectAsync
          .map(toOneValue)

      private def toDone: Any => Done =
        _ => Done

      private def toOneValue(rs: Seq[Row]): Long =
        rs.headOption.getOrElse(throw new RuntimeException("Boom")).getLong(ValueColumn)
  }

  // MODEL

  trait Bucket {
    def rowTime: Instant
    def colTime: Instant
    def tableName: String
  }

  type BucketOn = Instant => Bucket

  object BucketOn {

    val DayBucketTable       = "day"
    val MonthBucketTable     = "month"
    val MonthYearBucketTable = "year"
    val WeekYearBucketTable  = "year_week"
    val AlwaysBucketTable    = "forever"

    private case class
      BucketFor( instant   : Instant
               , rowTime   : Instant
               , colTime   : Instant
               , tableName : String
               ) extends Bucket

    private def apply(rowTimeUnit: ChronoUnit, colTimeUnit: ChronoUnit, table: String): BucketOn =
      instant => BucketFor(
          instant = instant
        , rowTime = truncatedTo(rowTimeUnit)(instant)
        , colTime = truncatedTo(colTimeUnit)(instant)
        , tableName = table
      )

    def Day: BucketOn =
      BucketOn(DAYS, HOURS, DayBucketTable)

    def Month: BucketOn =
      BucketOn(MONTHS, DAYS, MonthBucketTable)

    def MonthYear: BucketOn =
      BucketOn(YEARS, MONTHS, MonthYearBucketTable)

    def WeekYear: BucketOn =
      BucketOn(YEARS, WEEKS, WeekYearBucketTable)

    def Always: BucketOn =
      instant =>
        BucketFor(
            instant   = instant
          , rowTime   = Instant.EPOCH
          , colTime   = truncatedTo(YEARS)(instant)
          , tableName = AlwaysBucketTable
        )

    def All: Seq[BucketOn] =
      Seq(Day, Month, MonthYear, WeekYear, Always)

    def truncatedTo(chronoUnit: ChronoUnit)(instant: Instant): Instant = {

      def delegate(chronoUnit: ChronoUnit, instant: LocalDateTime): LocalDateTime =
        chronoUnit match {
          case YEARS  =>
            instant.truncatedTo(DAYS).`with`(firstDayOfYear())
          case MONTHS =>
            instant.truncatedTo(DAYS).`with`(firstDayOfMonth())
          case WEEKS  =>
            val today = instant.truncatedTo(DAYS)
            today.minusDays(today.getDayOfWeek().getValue() - FirstDayOfWeek.getValue())
          case _  =>
            instant.truncatedTo(chronoUnit)
        }

      val shadowed = LocalDateTime.ofInstant(instant, TimeZoneOffset)
      val shadow   = delegate(chronoUnit, shadowed)
      shadow.toInstant(TimeZoneOffset)
    }
  }

  // UTILS

  implicit class StatementUtil(statement: Statement[_])(implicit session: CassandraSession, system: ActorSystem[_]) {

    def selectAsync: Future[Seq[Row]] =
      session.select(statement).runWith(Sink.seq)

    def updateAsync: Future[Done] =
      session.executeWrite(statement)
  }
}
