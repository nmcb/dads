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

    val config = ConfigFactory.defaultApplication()
    val system = ActorSystem[Nothing](Behaviors.empty, "MainSystem", config)

    new Main(system).run()
  }
}

class Main(system: ActorSystem[_]) {

  import akka.actor.CoordinatedShutdown
  import org.slf4j._

  CoordinatedShutdown(system)
    .addJvmShutdownHook {
      LoggerFactory
        .getLogger(Main.getClass)
        .info("DADS Shutdown")
    }

  def run(): Unit =
    system.log.info("DADS Starting...")
}