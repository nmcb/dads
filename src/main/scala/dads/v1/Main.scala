/*
 * This is free and unencumbered software released into the public domain.
 */

package dads
package v1

import com.typesafe.config._

import akka.actor.typed._
import akka.actor.typed.scaladsl._

// #-shit
import akka.actor.{ActorSystem => ClassicActorSystem, ExtendedActorSystem}
import com.datastax.oss.driver.api.core.CqlSession
import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.immutable
import scala.compat.java8.FutureConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure
import akka.stream.alpakka.cassandra._
import javax.net.ssl.SSLContext

class CassandraSSLSessionProvider(system: ClassicActorSystem, config: Config) extends CqlSessionProvider {
  override def connect()(implicit ec: ExecutionContext): Future[CqlSession] = {
      val driverConfig = CqlSessionProvider.driverConfig(system, config)
      val driverConfigLoader = DriverConfigLoaderFromConfig.fromConfig(driverConfig)
      CqlSession.builder().withConfigLoader(driverConfigLoader).withSslContext(SSLContext.getDefault()).buildAsync().toScala
  }
}

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
    DadsSettings()

  def run(): Unit = {
    system.log.info("DADS Starting...")
    new MeasurementReceiver( settings.measurementReceiver
                           , CounterRepository(settings.repositorySettings)
                           , RealTimeDecimalRepository.cassandra(settings.repositorySettings)
                           ).run()
  }
}
