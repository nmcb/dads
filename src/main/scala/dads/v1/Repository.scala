/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1

import scala.concurrent.Future

trait Repository {

  import akka._
  import akka.actor.typed._
  import akka.stream.scaladsl._
  import akka.stream.alpakka.cassandra.scaladsl._

  import com.datastax.oss.driver.api.core.cql._

  implicit class StatementUtil(statement: Statement[_])(implicit session: CassandraSession, system: ActorSystem[_]) {

    def selectOptionAsync(): Future[Option[Row]] =
      session.select(statement).runWith(Sink.headOption)

    def selectSeqAsync(): Future[Seq[Row]] =
      session.select(statement).runWith(Sink.seq)

    def updateAsync(): Future[Done] =
      session.executeWrite(statement)
  }
}
