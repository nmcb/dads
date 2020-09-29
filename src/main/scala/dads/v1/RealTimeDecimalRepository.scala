/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1

import java.time._

import akka._
import akka.actor.typed._
import akka.stream.alpakka.cassandra._
import akka.stream.alpakka.cassandra.scaladsl._
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.querybuilder._
import dads.v1.CounterRepository.CounterValueColumn

import scala.collection._
import scala.concurrent._

object RealTimeDecimalRepository {

  case class Decimal(sourceId: SourceId, instant: Instant, value: Long)

  final val RealTimeDecimalTable = "realtime_decimal"

  final val SourceIdColumn  = "source"
  final val InstantIdColumn = "time"
  final val ValueColumn     = "value"

  def apply(settings: DadsSettings)(implicit system: ActorSystem[_]): RealTimeDecimalRepository =
    new RealTimeDecimalRepository {

      import akka.actor.typed.scaladsl.adapter._

      import QueryBuilder._

      implicit val executionContext: ExecutionContext =
        system.toClassic.dispatcher

      implicit val session: CassandraSession =
        CassandraSessionRegistry
          .get(system)
          .sessionFor(CassandraSessionSettings())

      private val cache: concurrent.Map[SourceId,Option[Decimal]] =
        concurrent.TrieMap()

      def readThrough(sourceId: SourceId, instant: Instant): Future[Decimal] = {
        val cached = cache.getOrElseUpdate(sourceId, None)

        cached.fold({

        },)
      }

      def set(decimal: Decimal): Future[Done] = {
        update(settings.realtimeKeyspace, RealTimeDecimalTable)
          .usingTtl(DadsSettings.RealTimeToLive.toSeconds.toInt)
          .setColumn(ValueColumn, literal(decimal.value))
          .whereColumn(SourceIdColumn).isEqualTo(literal(decimal.sourceId))
          .whereColumn(InstantIdColumn).isEqualTo(literal(decimal.instant.toEpochMilli))
          .build()
          .updateAsync()
      }

      private def toDecimal(rs: Option[Row]): Option[Long] =
        rs.map(_.getLong(ValueColumn))
    }
}

import RealTimeDecimalRepository._

trait RealTimeDecimalRepository extends Repository {

  def get(sourceId: SourceId, instant: Instant): Future[Option[Decimal]]

  def set(decimal: Decimal): Future[Done]
}
