/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1

import java.time._

import scala.collection._
import scala.collection.concurrent._
import scala.concurrent._

import akka._
import akka.actor.typed._
import akka.stream.alpakka.cassandra._
import akka.stream.alpakka.cassandra.scaladsl._

import com.datastax.oss.driver.api.core.cql._
import com.datastax.oss.driver.api.core.metadata.schema._
import com.datastax.oss.driver.api.querybuilder._

import squants.energy._

import transport._

object RealTimePowerRepository {

  import DadsSettings.RepositorySettings

  case class PowerAttribution(sourceId: SourceId, instant: Instant, value: Power)

  object PowerAttribution {

    def from(measurement: Measurement): PowerAttribution =
      PowerAttribution( sourceId = measurement.sourceId
             , instant  = measurement.timestamp
             , value    = measurement.reading
             )

    implicit val descendingPowerAttributionOrdering: Ordering[PowerAttribution] =
      (lhs, rhs) => lhs.instant.compareTo(rhs.instant)
  }


  def cassandra(settings: RepositorySettings)(implicit system: ActorSystem[_]): RealTimePowerRepository =
    new RealTimePowerRepository {

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

      def getAll(sourceId: SourceId): Future[Seq[PowerAttribution]] =
        selectFrom(settings.realtimeKeyspace, RealTimeDecimalTable)
          .column(SourceIdColumn)
          .column(InstantIdColumn)
          .column(ValueColumn)
          .whereColumn(SourceIdColumn).isEqualTo(literal(sourceId.uuid))
          .orderBy(InstantIdColumn, ClusteringOrder.DESC)
          .build()
          .selectSeqAsync()
          .map(toDecimals)

      override def getLast(sourceId: SourceId): Future[Option[PowerAttribution]] = {
        // FIXME retrieve last Decimal directly, cassandra provides
        getAll(sourceId).map(_.headOption)
      }

      override def set(decimal: PowerAttribution): Future[Done] = {
        update(settings.realtimeKeyspace, RealTimeDecimalTable)
          .usingTtl(DadsSettings.RealTimeToLive.toSeconds.toInt)
          .setColumn(ValueColumn, literal(decimal.value.toMilliwatts.toLong))
          .whereColumn(SourceIdColumn).isEqualTo(literal(decimal.sourceId.uuid))
          .whereColumn(InstantIdColumn).isEqualTo(literal(decimal.instant.toEpochMilli))
          .build()
          .updateAsync()
      }

      private def toDecimal(rs: Option[Row]): Option[Long] =
        rs.map(_.getLong(ValueColumn))

      private def toDecimals(rs: Seq[Row]): Seq[PowerAttribution] =
        rs.map(r =>
          PowerAttribution( r.getUuid(SourceIdColumn).toSourceId
                          , r.getInstant(InstantIdColumn)
                          , Milliwatts(r.getBigDecimal(ValueColumn).longValue) // TODO check truncation
                          ))
    }

  def memory(settings: DadsSettings)(implicit system: ActorSystem[_]): RealTimePowerRepository =
    new RealTimePowerRepository {

      import akka.actor.typed.scaladsl.adapter._

      implicit val executionContext: ExecutionContext =
        system.toClassic.dispatcher

      val storage: concurrent.Map[SourceId, Seq[PowerAttribution]] =
        TrieMap()

      override def getLast(sourceId: SourceId): Future[Option[PowerAttribution]] =
        Future
          .successful(storage.getOrElse(sourceId, None))
          .map(_.iterator.toSeq.sorted.headOption)

      override def set(decimal: PowerAttribution): Future[Done] =
        Future.successful{
          val decimals = storage.getOrElseUpdate(decimal.sourceId, Seq(decimal)).sorted
          if (decimals.head.instant.isBefore(decimal.instant)) storage.update(decimal.sourceId, decimal +: decimals)
          Done
        }
    }
}

import RealTimePowerRepository._

trait RealTimePowerRepository extends Repository {

  def getLast(sourceId: SourceId): Future[Option[PowerAttribution]]

  def set(decimal: PowerAttribution): Future[Done]
}
