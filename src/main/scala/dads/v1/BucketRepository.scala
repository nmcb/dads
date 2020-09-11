/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1

import java.time._
import java.util._

import com.datastax.oss.driver.api.core.cql._
import com.datastax.oss.driver.api.querybuilder._

import scala.concurrent._

import akka.actor.typed._
import akka.stream.alpakka.cassandra._
import akka.stream.alpakka.cassandra.scaladsl._

trait BucketRepository {

  import BucketRepository._

  def addToDayBucket(m: Measurement): Future[Boolean]

  def readDay(sourceId: UUID, dayStart: Instant, hourStart: Instant): Future[Option[Measurement]]

  def addToMonthBucket(m: Measurement): Future[Boolean]

  def addToYearBucket(m: Measurement): Future[Boolean]

  def addToWeekYearBucket(m: Measurement): Future[Boolean]

  def addToForeverBucket(m: Measurement): Future[Boolean]
}

object BucketRepository {

  case class Measurement(sourceId: UUID, time: Instant, value: Long)

  trait Result {
    def wasApplied: Boolean
  }

  import QueryBuilder._

  val ValueColumn   = "value"
  val SourceColumn  = "source"
  val RowTimeColumn = "rowtime"
  val ColTimeColumn = "coltime"

  def apply(settings: DadsSettings)(system: ActorSystem[_]): BucketRepository =
    new BucketRepository {

      import akka.actor.typed.scaladsl.adapter._

      implicit val executionContext: ExecutionContext =
        system.toClassic.dispatcher

      implicit val session: CassandraSession =
        CassandraSessionRegistry
          .get(system)
          .sessionFor(CassandraSessionSettings())

      def addTo(bucket: BucketFor): SimpleStatement =
        update(settings.bucketKeyspace, bucket.tableName)
          .increment(ValueColumn, literal(bucket.measurement.value))
          .whereColumn(SourceColumn).isEqualTo(literal(bucket.measurement.sourceId))
          .whereColumn(RowTimeColumn).isEqualTo(literal(bucket.rowTime.toEpochMilli))
          .whereColumn(ColTimeColumn).isEqualTo(literal(bucket.colTime.toEpochMilli))
          .build()

      override def addToDayBucket(measurement: Measurement): Future[Boolean] =
        addTo(BucketFor.Day(measurement)).execAsync.map(_.wasApplied)

      override def readDay(sourceId: UUID, rowTime: Instant, colTime: Instant): Future[Option[Measurement]] =
        ???

      override def addToMonthBucket(measurement: Measurement): Future[Boolean] =
        addTo(BucketFor.Month(measurement)).execAsync.map(_.wasApplied)

      override def addToYearBucket(measurement: Measurement): Future[Boolean] =
        addTo(BucketFor.Year(measurement)).execAsync.map(_.wasApplied)

      override def addToWeekYearBucket(measurement: Measurement): Future[Boolean] =
        addTo(BucketFor.WeekYear(measurement)).execAsync.map(_.wasApplied)

      override def addToForeverBucket(measurement: Measurement): Future[Boolean] =
        addTo(BucketFor.Forever(measurement)).execAsync.map(_.wasApplied)
  }

  // Model

  import java.time._

  case class BucketMeasurementRow(sourceId: UUID, rowTime: Instant, colTime: Instant, value: Long)

  case class BucketFor( tableName: String
                      , measurement: Measurement
                      , rowTime: Instant
                      , colTime: Instant
                      )

  object BucketFor {

    import java.time.temporal._

    private final val UTC = ZoneOffset.UTC

    def apply(table: String)(rowTruncUnit: ChronoUnit)(colTruncUnit: ChronoUnit): Measurement => BucketFor =
      underlying =>
        new BucketFor( tableName   = table
                     , measurement = underlying
                     , rowTime = LocalDateTime.ofInstant(underlying.time, UTC).truncatedTo(rowTruncUnit).toInstant(UTC)
                     , colTime = LocalDateTime.ofInstant(underlying.time, UTC).truncatedTo(colTruncUnit).toInstant(UTC)
                     )

    def Day: Measurement => BucketFor =
      BucketFor("day")(ChronoUnit.DAYS)(ChronoUnit.HOURS)

    def Month: Measurement => BucketFor =
      BucketFor("month")(ChronoUnit.MONTHS)(ChronoUnit.DAYS)

    def Year: Measurement => BucketFor =
      BucketFor("year")(ChronoUnit.YEARS)(ChronoUnit.MONTHS)

    def WeekYear: Measurement => BucketFor =
      BucketFor("week_year")(ChronoUnit.YEARS)(ChronoUnit.WEEKS)

    def Forever: Measurement =>  BucketFor =
      underlying => new BucketFor(
          tableName   = "forever"
        , measurement = underlying
        , rowTime     = Instant.EPOCH
        , colTime     = LocalDateTime.ofInstant(underlying.time, UTC).truncatedTo(ChronoUnit.YEARS).toInstant(UTC)
      )
  }

  // Utils

  implicit class StatementUtil(statement: Statement[_])(implicit session: CassandraSession, executionContext: ExecutionContext) {

    import scala.compat.java8._

    def execAsync: Future[AsyncResultSet] =
      session.underlying().flatMap(session => FutureConverters.toScala(session.executeAsync(statement)))
  }
}
