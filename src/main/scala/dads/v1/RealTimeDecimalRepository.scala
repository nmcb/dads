/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1

import java.time._

import akka._
import akka.actor.typed._
import akka.stream.alpakka.cassandra._
import akka.stream.alpakka.cassandra.scaladsl._

import com.datastax.oss.driver.api.core.cql._
import com.datastax.oss.driver.api.core.metadata.schema._
import com.datastax.oss.driver.api.querybuilder._

import scala.collection._
import scala.collection.concurrent._
import scala.concurrent._

import transport._

object RealTimeDecimalRepository {

  case class Decimal(sourceId: SourceId, instant: Instant, value: BigDecimal)

  object Decimal {

    def from(measurement: Measurement): Decimal =
      Decimal( sourceId = measurement.sourceId
             , instant  = measurement.timestamp
             , value    = measurement.reading
             )
  }

  implicit val decimalInstantDescendingOrdering: Ordering[Decimal] =
    (lhs, rhs) => lhs.instant.compareTo(rhs.instant)

  def cassandra(settings: DadsSettings)(implicit system: ActorSystem[_]): RealTimeDecimalRepository =
    new RealTimeDecimalRepository {

      final val RealTimeDecimalTable = "realtime_decimal"

      final val SourceIdColumn  = "source"
      final val InstantIdColumn = "time"
      final val ValueColumn     = "value"

      import akka.actor.typed.scaladsl.adapter._

      import QueryBuilder._

      implicit val executionContext: ExecutionContext =
        system.toClassic.dispatcher

      implicit val session: CassandraSession =
        CassandraSessionRegistry
          .get(system)
          .sessionFor(CassandraSessionSettings())

      def getAll(sourceId: SourceId): Future[Seq[Decimal]] =
        selectFrom(settings.realtimeKeyspace, RealTimeDecimalTable)
          .column(SourceIdColumn)
          .column(InstantIdColumn)
          .column(ValueColumn)
          .whereColumn(SourceIdColumn).isEqualTo(literal(sourceId))
          .orderBy(InstantIdColumn, ClusteringOrder.DESC)
          .build()
          .selectSeqAsync()
          .map(toDecimals)

      override def getLast(sourceId: SourceId): Future[Option[Decimal]] =
        getAll(sourceId).map(_.headOption)

      override def set(decimal: Decimal): Future[Done] = {
        update(settings.realtimeKeyspace, RealTimeDecimalTable)
          .usingTtl(DadsSettings.RealTimeToLive.toSeconds.toInt)
          .setColumn(ValueColumn, literal(decimal.value.toLong))
          .whereColumn(SourceIdColumn).isEqualTo(literal(decimal.sourceId))
          .whereColumn(InstantIdColumn).isEqualTo(literal(decimal.instant.toEpochMilli))
          .build()
          .updateAsync()
      }

      private def toDecimal(rs: Option[Row]): Option[Long] =
        rs.map(_.getLong(ValueColumn))

      private def toDecimals(rs: Seq[Row]): Seq[Decimal] =
        rs.map(r =>
          Decimal( r.getUuid(SourceIdColumn)
                 , r.getInstant(InstantIdColumn)
                 , r.getBigDecimal(ValueColumn)
                 ))
    }

  def memory(settings: DadsSettings)(implicit system: ActorSystem[_]): RealTimeDecimalRepository =
    new RealTimeDecimalRepository {

      import akka.actor.typed.scaladsl.adapter._

      implicit val executionContext: ExecutionContext =
        system.toClassic.dispatcher

      val storage: concurrent.Map[SourceId, Seq[Decimal]] =
        TrieMap()

      override def getLast(sourceId: SourceId): Future[Option[Decimal]] =
        Future
          .successful(storage.getOrElse(sourceId, None))
          .map(_.iterator.toSeq.sorted.headOption)

      override def set(decimal: Decimal): Future[Done] =
        Future.successful{
          val decimals = storage.getOrElseUpdate(decimal.sourceId, Seq(decimal)).sorted
          if (decimals.head.instant.isBefore(decimal.instant)) storage.update(decimal.sourceId, decimal +: decimals)
          Done
        }
    }
}

import RealTimeDecimalRepository._

trait RealTimeDecimalRepository extends Repository {

  def getLast(sourceId: SourceId): Future[Option[Decimal]]

  def set(decimal: Decimal): Future[Done]
}
