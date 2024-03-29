/*
 * This is free and unencumbered software released into the public domain.
 */

package dads
package v1

import com.typesafe.config._

import akka.actor.typed._
import akka.actor.typed.scaladsl._

object Main {

  def main(args: Array[String]): Unit = {

    implicit val system: ActorSystem[Nothing] =
      ActorSystem[Nothing]( Behaviors.empty
                          , "DadsMainActorSystem"
                          , ConfigFactory.defaultApplication.resolve
                          )
    new Main().run()
  }
}

class Main(implicit system: ActorSystem[_]) {

  import akka.actor._
  import org.slf4j._

  CoordinatedShutdown(system)
    .addJvmShutdownHook(
      LoggerFactory
        .getLogger(Main.getClass)
        .info("DADS Stopped"))

  lazy val settings: DadsSettings =
    new DadsSettings()

  def run(): Unit = {
    system.log.info("DADS Starting...")
    new MeasurementReceiver( settings.measurementReceiver
                           , CounterRepository(settings.repositorySettings)
                           , RealTimeDecimalRepository.cassandra(settings.repositorySettings)
                           ).run()
  }
}
