/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package util

import scala.util._
import scala.concurrent._

import com.typesafe.config._

import akka._
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.stream.alpakka.cassandra._
import akka.stream.alpakka.cassandra.scaladsl._


object InitCassandra {

  val scripts: Seq[String] =
    Seq( Resources.load("/cassandra/initialise.cql")
       , Resources.load("/cassandra/cassandra.v1.cql")
       )

  def main(args: Array[String]): Unit = {

    val config: Config =
      ConfigFactory.defaultApplication()

    val system: ActorSystem[Nothing] =
      ActorSystem[Nothing](Behaviors.empty, "InitCassandraSystem", config)

    val session: CassandraSession =
      CassandraSessionRegistry
        .get(system)
        .sessionFor(CassandraSessionSettings())

    implicit val executionContext =
      system.executionContext

    val task: Future[Done] =
      Future.sequence(scripts.map(script => session.executeDDL(script))).map(_ => Done)

    task.onComplete {
      case Success(_) =>
        println("Cassandra initialized.")
        system.terminate()
      case Failure(e) =>
        println(s"Cassandra initialization failed.")
        e.printStackTrace
        system.terminate()
    }
  }
}
