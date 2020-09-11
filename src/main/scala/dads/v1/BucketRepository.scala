/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1

import java.time._
import java.util._

import scala.concurrent._

import com.datastax.oss.driver.api.core.cql._
import com.datastax.oss.driver.api.querybuilder._

import akka.actor.typed._
import akka.stream.scaladsl._
import akka.stream.alpakka.cassandra._
import akka.stream.alpakka.cassandra.scaladsl._

trait BucketRepository {

  import BucketRepository._

  def addTo(bucketFor: BucketFor)(measurement: Measurement): Future[Unit]

  def addToAll(measurement: Measurement): Future[Unit]

  def getFrom(bucket: BucketFor)(sourceId: UUID): Future[Long]
}

object BucketRepository {

  case class Measurement( sourceId : UUID
                        , instant  : Instant
                        , value    : Long)
  import temporal._
  import QueryBuilder._
  import BucketFor._

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

      def addTo(bucketFor: BucketFor)(measurement: Measurement): Future[Unit] =
        update(settings.bucketKeyspace, bucketFor.tableName)
          .increment(ValueColumn, literal(measurement.value))
          .whereColumn(SourceColumn).isEqualTo(literal(measurement.sourceId))
          .whereColumn(RowTimeColumn).isEqualTo(literal(bucketFor.rowTime.toEpochMilli))
          .whereColumn(ColTimeColumn).isEqualTo(literal(bucketFor.colTime.toEpochMilli))
          .build()
          .execAsync
          .map(toUnit)

      override def addToAll(measurement: Measurement): Future[Unit] =
        Future.sequence(
          Seq(Day, Month, Year, WeekYear, Forever)
            .map(bucket => bucket(measurement.instant))
            .map(addTo(_)(measurement))
        ).map(toUnit)

      override def getFrom(bucket: BucketFor)(sourceId: UUID): Future[Long] =
        selectFrom(settings.bucketKeyspace, bucket.tableName)
          .column(ValueColumn)
          .whereColumn(SourceColumn).isEqualTo(literal(sourceId))
          .whereColumn(RowTimeColumn).isEqualTo(literal(bucket.rowTime.toEpochMilli))
          .whereColumn(ColTimeColumn).isEqualTo(literal(bucket.colTime.toEpochMilli))
          .build()
          .execAsync
          .map(toOneValue)

      private def toUnit: Any => Unit =
        _ => ()

      private def toOneValue(rs: Seq[Row]): Long =
        rs.headOption.getOrElse(throw new RuntimeException("Boom")).getLong(ValueColumn)
  }

  // Model

  import java.time._

  val DayBucketTable      = "day"
  val MonthBucketTable    = "month"
  val YearBucketTable     = "year"
  val WeekYearBucketTable = "week_year"
  val ForeverBucketTable  = "forever"

  case class BucketFor( instant   : Instant
                      , rowTime   : Instant
                      , colTime   : Instant
                      , tableName : String
                      )

  object BucketFor {

    def apply(rowTruncUnit: ChronoUnit)(colTruncUnit: ChronoUnit)(table: String): Instant => BucketFor =
      instant => BucketFor(
          instant = instant
        , rowTime = truncatedTo(rowTruncUnit)(instant)
        , colTime = truncatedTo(colTruncUnit)(instant)
        , tableName = table
      )

    def Day: Instant => BucketFor =
      BucketFor(ChronoUnit.DAYS)(ChronoUnit.HOURS)(DayBucketTable)

    def Month: Instant => BucketFor =
      BucketFor(ChronoUnit.MONTHS)(ChronoUnit.DAYS)(MonthBucketTable)

    def Year: Instant => BucketFor =
      BucketFor(ChronoUnit.YEARS)(ChronoUnit.MONTHS)(YearBucketTable)

    def WeekYear: Instant => BucketFor =
      BucketFor(ChronoUnit.YEARS)(ChronoUnit.WEEKS)(WeekYearBucketTable)

    def Forever: Instant => BucketFor =
      instant =>
        BucketFor(
            instant   = instant
          , rowTime   = Instant.EPOCH
          , colTime   = truncatedTo(ChronoUnit.YEARS)(instant)
          , tableName = ForeverBucketTable
        )
  }

  // Utils

  implicit class StatementUtil(statement: Statement[_])(implicit session: CassandraSession, system: ActorSystem[_]) {

    def execAsync: Future[Seq[Row]] =
      session.select(statement).runWith(Sink.seq)
  }

  private final val UTC: ZoneOffset =
    ZoneOffset.UTC

  private val truncatedTo: ChronoUnit => Instant => Instant =
    chronoUnit => underlying => LocalDateTime.ofInstant(underlying, UTC).truncatedTo(chronoUnit).toInstant(UTC)
}
