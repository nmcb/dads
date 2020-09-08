/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1

import java.time._
import java.util._

import akka._
import akka.actor.typed._
import akka.stream.alpakka.cassandra._
import akka.stream.alpakka.cassandra.scaladsl._

import scala.concurrent._

case class Measurement(sourceId: UUID, rowTime: LocalDate, colTime: LocalDate, value: Long)
case class RealtimeMeasurement(sourceId: UUID, time: LocalDate, value: BigDecimal)

trait Repository {
  def insertDay(m: Measurement): Future[Done]

  def insertMonth(m: Measurement): Future[Done]

  def insertYear(m: Measurement): Future[Done]

  def insertYearWeek(m: Measurement): Future[Done]

  def insertForever(m: Measurement): Future[Done]

  def insertRealtimeDecimal(m: RealtimeMeasurement): Future[Done]
}

object Repository {

  def apply(cassandraSessionSettings: CassandraSessionSettings)(implicit system: ActorSystem[_]): Repository =
    new Repository {

      import akka.actor.typed.scaladsl.adapter._

      val cassandraKeyspace: String =
        "dads"

      implicit val executionContext: ExecutionContext =
        system.toClassic.dispatcher

      implicit val cassandraSession: CassandraSession =
        CassandraSessionRegistry
          .get(system)
          .sessionFor(cassandraSessionSettings)

      def insertCumulative(table: String, m: Measurement): String =
        s""" INSERT INTO $cassandraKeyspace.$table(source, rowtime, coltime, value)
           | VALUES (${m.sourceId.toString}, ${m.rowTime.toString}, ${m.colTime.toString}, ${m.value.toString})
         """.stripMargin

      def insertRealtime(table: String, m: RealtimeMeasurement): String =
        s""" INSERT INTO $cassandraKeyspace.$table(source, time, value)
           | VALUES (${m.sourceId.toString}, ${m.time.toString}, ${m.value.toString})
         """.stripMargin

      override def insertDay(measurement: Measurement): Future[Done] =
        cassandraSession.executeWrite(insertCumulative("day", measurement))

      override def insertMonth(measurement: Measurement): Future[Done] =
        cassandraSession.executeWrite(insertCumulative("month", measurement))

      override def insertYear(measurement: Measurement): Future[Done] =
        cassandraSession.executeWrite(insertCumulative("year", measurement))

      override def insertYearWeek(measurement: Measurement): Future[Done] =
        cassandraSession.executeWrite(insertCumulative("year_week", measurement))

      override def insertForever(measurement: Measurement): Future[Done] =
        cassandraSession.executeWrite(insertCumulative("forever", measurement))

      override def insertRealtimeDecimal(measurement: RealtimeMeasurement): Future[Done] =
        cassandraSession.executeWrite(insertRealtime("realtime_decimal", measurement))
    }
}
