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

      final val RealTimePowerAttributionTable = "realtime_decimal"

      final val SourceIdColumn  = "source"
      final val InstantIdColumn = "time"
      final val PowerColumn     = "value"

      import akka.actor.typed.scaladsl.adapter._

      import QueryBuilder._

      implicit val executionContext: ExecutionContext =
        system.toClassic.dispatcher

      implicit val session: CassandraSession =
        CassandraSessionRegistry
          .get(system)
          .sessionFor(CassandraSessionSettings())

      def getAll(sourceId: SourceId): Future[Seq[PowerAttribution]] =
        selectFrom(settings.realtimeKeyspace, RealTimePowerAttributionTable)
          .column(SourceIdColumn)
          .column(InstantIdColumn)
          .column(PowerColumn)
          .whereColumn(SourceIdColumn).isEqualTo(literal(sourceId.uuid))
          .orderBy(InstantIdColumn, ClusteringOrder.DESC)
          .build()
          .selectSeqAsync()
          .map(toAttributions)

      override def getLast(sourceId: SourceId): Future[Option[PowerAttribution]] = {
        // FIXME retrieve last attribution directly, cassandra provides ordering on the cluster column 'time'
        getAll(sourceId).map(_.headOption)
      }

      override def set(decimal: PowerAttribution): Future[Done] = {
        // FIXME hides mapping to milli-watts, dual during retrieval
        update(settings.realtimeKeyspace, RealTimePowerAttributionTable)
          .usingTtl(DadsSettings.RealTimeToLive.toSeconds.toInt)
          .setColumn(PowerColumn, literal(decimal.value.toMilliwatts.toLong))
          .whereColumn(SourceIdColumn).isEqualTo(literal(decimal.sourceId.uuid))
          .whereColumn(InstantIdColumn).isEqualTo(literal(decimal.instant.toEpochMilli))
          .build()
          .updateAsync()
      }

      private def toAttributions(rs: Seq[Row]): Seq[PowerAttribution] =
        rs.map(r =>
          PowerAttribution( r.getUuid(SourceIdColumn).toSourceId
                          , r.getInstant(InstantIdColumn)
                          , Milliwatts(r.getBigDecimal(PowerColumn).longValue) // TODO check truncation
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

      override def set(attribution: PowerAttribution): Future[Done] =
        Future.successful{
          val attributions = storage.getOrElseUpdate(attribution.sourceId, Seq(attribution)).sorted
          if (attributions.head.instant.isBefore(attribution.instant)) storage.update(attribution.sourceId, attribution +: attributions)
          Done
        }
    }
}

import RealTimePowerRepository._

trait RealTimePowerRepository extends Repository {

  def getLast(sourceId: SourceId): Future[Option[PowerAttribution]]

  def set(decimal: PowerAttribution): Future[Done]
}
