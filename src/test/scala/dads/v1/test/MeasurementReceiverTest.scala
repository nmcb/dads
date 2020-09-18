/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package test

import com.typesafe.config._

import scala.concurrent._

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.grpc._
import akka.http.scaladsl._

import org.scalatest._
import org.scalatest.concurrent._
import org.scalatest.matchers.should._
import org.scalatest.wordspec._

import akka.actor.testkit.typed.scaladsl._


class MeasurementReceiverTest
  extends AnyWordSpec
    with BeforeAndAfter
    with Matchers
    with ScalaFutures
    with RealWorld
{
  import transport.grpc._
  import v1.MeasurementDataCnf
  import v1.MeasurementDataInd
  import v1.MeasurementServiceClient

  val testKit: ActorTestKit =
    ActorTestKit(ConfigFactory.defaultApplication)

  val settings: DadsSettings.ReceiverSettings =
    DadsSettings().measurementReceiver

  val futureHttpServerBinding: Future[Http.ServerBinding] =
    new MeasurementReceiver(settings)(testKit.system).run()

  val httpServerBinding: Http.ServerBinding =
    futureHttpServerBinding.futureValue

  implicit val clientSystem: ActorSystem[_] =
    ActorSystem(Behaviors.empty, "MeasurementServiceClient")

  after {
    clientSystem.terminate()
    httpServerBinding.terminate(Main.RealTimeServiceLevelAgreement)
  }

  val client: MeasurementServiceClient =
    MeasurementServiceClient(
      GrpcClientSettings
        .connectToServiceAt(settings.host, settings.port)
        .withTls(false)) // FIXME Should not be used in production

  "MeasurementReceiver" should {
    "process a single measurement data indication" in {
      val reply = client.process(MeasurementDataInd())
      reply.futureValue should be (MeasurementDataCnf.defaultInstance)
    }
  }
}
