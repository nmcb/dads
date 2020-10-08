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
    with ArbitraryRequests
{
  import Arbitrary._

  import DadsSettings._

  import transport.grpc._
  import v1.MeasurementDataCnf
  import v1.MeasurementDataInd
  import v1.MeasurementServiceClient

  val testKit: ActorTestKit =
    ActorTestKit(ConfigFactory.defaultApplication.resolve)

  val settings: DadsSettings =
    new DadsTestSettings()

  implicit val clientSystem: ActorSystem[_] =
    ActorSystem(Behaviors.empty, "MeasurementServiceClient")

  val futureHttpServerBinding: Future[Http.ServerBinding] =
    new MeasurementReceiver( settings.measurementReceiver
                           , CounterRepository(settings.repositorySettings)
                           , RealTimeDecimalRepository.cassandra(settings.repositorySettings))(testKit.system).run()

  val httpServerBinding: Http.ServerBinding =
    futureHttpServerBinding.futureValue

  override protected def afterAll(): Unit = {
    clientSystem.terminate()
    httpServerBinding.terminate(RealTimeServiceLevelAgreement)
  }

  val client: MeasurementServiceClient =
    MeasurementServiceClient(
      GrpcClientSettings
        .connectToServiceAt("127.0.0.1", settings.measurementReceiver.port)
        .withTls(false)) // FIXME should not be used in production

  "MeasurementReceiver" should {
    "process an arbitrary measurement data indication" in { // TODO can we get forAll to work here ?
      val ind = arbitrary[MeasurementDataInd].sample.get
      val task  = client.process(ind)
      task.futureValue should be (MeasurementDataCnf(ind.messageId))
    }
  }
}
