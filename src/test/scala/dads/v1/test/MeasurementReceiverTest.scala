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

import org.scalacheck._

import akka.actor.testkit.typed.scaladsl._

import data._

class MeasurementReceiverTest
  extends AnyWordSpec
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures
    with MeasurementReceiverData
    with RealWorld
{
  import Arbitrary._
  import ArbitraryRequests._

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

  override protected def afterAll(): Unit = {
    clientSystem.terminate()
    httpServerBinding.terminate(Main.RealTimeServiceLevelAgreement)
  }

  val client: MeasurementServiceClient =
    MeasurementServiceClient(
      GrpcClientSettings
        .connectToServiceAt(settings.host, settings.port)
        .withTls(false)) // FIXME should not be used in production

  // FIXME Use arbitrary indications
  "MeasurementReceiver" should {
    "process a single measurement data indication" in {
      val ind = arbitrary[MeasurementDataInd].sample.getOrElse(throw new RuntimeException("booms"))
      val task  = client.process(ind)
      val reply = task.futureValue
      println(reply)
      reply should be (MeasurementDataCnf(ind.messageId))
    }
  }
}
