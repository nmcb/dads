/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1

import java.time._
import java.util._

import scala.concurrent._
import scala.concurrent.duration._

import akka._
import akka.actor.typed._
import akka.stream.alpakka.cassandra._
import akka.stream.alpakka.cassandra.scaladsl._

case class Measurement(sourceId: UUID, rowTime: Instant, colTime: Instant, value: Long)
case class RealtimeMeasurement(sourceId: UUID, time: Instant, value: BigDecimal)

trait Repository {

  def insertDay(m: Measurement): Future[Done]

  def readDay(sourceId: UUID, rowTime: Instant, colTime: Instant): Future[Option[Measurement]]

  def insertMonth(m: Measurement): Future[Done]

  def insertYear(m: Measurement): Future[Done]

  def insertYearWeek(m: Measurement): Future[Done]

  def insertForever(m: Measurement): Future[Done]

  def insertRealtimeDecimal(m: RealtimeMeasurement): Future[Done]
}

object Repository {

  val keyspace: String =
    "dads"

  def apply(implicit system: ActorSystem[_]): Repository =
    new Repository {

      import akka.actor.typed.scaladsl.adapter._

      implicit val executionContext: ExecutionContext =
        system.toClassic.dispatcher

      implicit val session: CassandraSession =
        CassandraSessionRegistry
          .get(system)
          .sessionFor(CassandraSessionSettings())

      def insertCumulative(table: String, m: Measurement): String =
        s""" UPDATE $keyspace.$table
           | SET     value=value + ${m.value.toString}
           | WHERE  source=${m.sourceId.toString}
           | AND   rowtime=${m.rowTime.toEpochMilli}
           | AND   coltime=${m.colTime.toEpochMilli}
         """.stripMargin

      def readCumulative(table: String, sourceId: UUID, rowTime: Instant, colTime: Instant): String =
        s""" SELECT source, rowtime, coltime, value
           | FROM   $keyspace.$table
           | WHERE  source=${sourceId.toString}
           | AND   rowtime=${rowTime.toEpochMilli}
           | AND   coltime=${colTime.toEpochMilli}
         """.stripMargin

      def insertRealtime(table: String, m: RealtimeMeasurement): String =
        s""" INSERT INTO $keyspace.$table(source, time, value)
           | VALUES (${m.sourceId.toString}, ${m.time.toEpochMilli}, ${m.value.toString})
         """.stripMargin

      override def insertDay(measurement: Measurement): Future[Done] =
        session.executeWrite(insertCumulative("day", measurement))

      override def readDay(sourceId: UUID, rowTime: Instant, colTime: Instant): Future[Option[Measurement]] =
        ???

      override def insertMonth(measurement: Measurement): Future[Done] =
        session.executeWrite(insertCumulative("month", measurement))

      override def insertYear(measurement: Measurement): Future[Done] =
        session.executeWrite(insertCumulative("year", measurement))

      override def insertYearWeek(measurement: Measurement): Future[Done] =
        session.executeWrite(insertCumulative("year_week", measurement))

      override def insertForever(measurement: Measurement): Future[Done] =
        session.executeWrite(insertCumulative("forever", measurement))

      override def insertRealtimeDecimal(measurement: RealtimeMeasurement): Future[Done] =
        session.executeWrite(insertRealtime("realtime_decimal", measurement))
    }

  def createTables(system: ActorSystem[_]): Unit = {

    val session: CassandraSession =
      CassandraSessionRegistry
        .get(system)
        .sessionFor(CassandraSessionSettings())

    val createKeyspaceIfNotExist =
      s""" CREATE KEYSPACE IF NOT EXISTS ${keyspace}
         | WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 }
       """.stripMargin

    def createCumulativeIfNotExist(table: String) =
      s""" CREATE TABLE IF NOT EXISTS ${keyspace}.${table} (
         | source uuid,
         | rowtime timestamp,
         | coltime timestamp,
         | value   counter,
         | primary key ((source, rowtime), coltime))
       """.stripMargin

    // OK to block here, main thread
    Await.ready(session.executeDDL(createKeyspaceIfNotExist), 30.seconds)
    system.log.info(s"Created keyspace: $keyspace")

    Seq("day", "month", "year_week", "year", "forever").foreach { table =>
      Await.ready(session.executeDDL(createCumulativeIfNotExist(table)), 30.seconds)
      system.log.info(s"Created table: $table")
    }
  }
}
