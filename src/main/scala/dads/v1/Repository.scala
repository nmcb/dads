/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1

import java.time._
import java.util._

import com.datastax.oss.driver.api.core.cql._
import com.datastax.oss.driver.api.querybuilder._

import scala.concurrent._

import akka._
import akka.actor.typed._
import akka.stream.alpakka.cassandra._
import akka.stream.alpakka.cassandra.scaladsl._

case class Measurement(sourceId: UUID, rowTime: Instant, colTime: Instant, value: Long)
case class RealtimeMeasurement(sourceId: UUID, time: Instant, value: BigDecimal)

trait Repository {

  def insertDay(m: Measurement): Future[Boolean]

  def readDay(sourceId: UUID, rowTime: Instant, colTime: Instant): Future[Option[Measurement]]

  def insertMonth(m: Measurement): Future[Done]

  def insertYear(m: Measurement): Future[Done]

  def insertYearWeek(m: Measurement): Future[Done]

  def insertForever(m: Measurement): Future[Done]

  def insertRealtimeDecimal(m: RealtimeMeasurement): Future[Done]
}

object Repository {

  trait Result {
    def wasApplied: Boolean
  }

  import QueryBuilder._

  val keyspace: String =
    "aurum"

  def apply(system: ActorSystem[_]): Repository =
    new Repository {

      import akka.actor.typed.scaladsl.adapter._

      implicit val executionContext: ExecutionContext =
        system.toClassic.dispatcher

      implicit val session: CassandraSession =
        CassandraSessionRegistry
          .get(system)
          .sessionFor(CassandraSessionSettings())

      private def insertCumulative(table: String, m: Measurement): SimpleStatement =
        update(keyspace, table)
          .increment("value", literal(m.value))
          .whereColumn("source").isEqualTo(literal(m.sourceId))
          .whereColumn("rowtime").isEqualTo(literal(m.rowTime.toEpochMilli))
          .whereColumn("coltime").isEqualTo(literal(m.colTime.toEpochMilli))
          .build()

      override def insertDay(measurement: Measurement): Future[Boolean] =
        insertCumulative("day", measurement).execAsync.map(_.wasApplied)

      override def readDay(sourceId: UUID, rowTime: Instant, colTime: Instant): Future[Option[Measurement]] =
        ???

      override def insertMonth(measurement: Measurement): Future[Done] =
        ???
//        session.executeWrite(insertCumulative("month", measurement))

      override def insertYear(measurement: Measurement): Future[Done] =
        ???

      override def insertYearWeek(measurement: Measurement): Future[Done] =
        ???

      override def insertForever(measurement: Measurement): Future[Done] =
        ???

      override def insertRealtimeDecimal(measurement: RealtimeMeasurement): Future[Done] =
        ???
  }

  // Utils

  implicit class StatementUtil(statement: Statement[_])(implicit session: CassandraSession, executionContext: ExecutionContext) {

    import scala.compat.java8._

    def execAsync: Future[AsyncResultSet] =
      session.underlying().flatMap(session => FutureConverters.toScala(session.executeAsync(statement)))
  }
}
