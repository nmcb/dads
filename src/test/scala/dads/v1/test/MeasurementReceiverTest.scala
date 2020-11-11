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

  import transport.grpc.v2._

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
    "process an arbitrary measurement data request" in { // TODO can we get forAll to work here ?
      val req = arbitrary[MeasurementDataInd].sample.get
      val task  = client.process(req)
      task.futureValue should be (MeasurementDataCnf(req.messageId))
    }
  }

  // KNOWN BUGS

  val issue_11: MeasurementDataInd =
    MeasurementDataInd( "c9d77b3b-3036-495a-bcee-104c93ce3a16"
                      , Some(DeviceIdentity(1))
                      , List( MeasurementData( "fb810cbe-ddef-4ccb-a568-78ec6319a185"
                                             , 7345
                                             , "kWh"
                                             , Vector( MeasurementValues( pastNow.toEpochMilli
                                                                        , Some( MultiType(MultiType.Value.Decimal("0.0")))
                                                                        )))
                            , MeasurementData( "33f34fc1-bcad-40b8-adbf-b8908915baee"
                                             , 7345
                                             , "kW"
                                             , Vector( MeasurementValues( pastNow.toEpochMilli
                                                                        , Some(MultiType(MultiType.Value.Decimal("0.338")))
                                                                        )))
                            , MeasurementData( "0532cac2-4b6e-48e2-8009-34117fdc860a"
                                             , 7345
                                             , "kWh"
                                             , Vector( MeasurementValues( pastNow.toEpochMilli
                                                                        , Some(MultiType(MultiType.Value.Decimal("1671.43")))
                                                                        )))
                            , MeasurementData( "72204a0c-9a20-452b-9ea4-dd1414458ab0"
                                             , 7345
                                             , "kWh"
                                             , Vector( MeasurementValues( pastNow.toEpochMilli
                                                                        , Some(MultiType(MultiType.Value.Decimal("1671.43")))
                                                                        )))
                            , MeasurementData( "d4404c4f-c717-480b-9b61-8f52aa34982a"
                                             , 7345
                                             , "kWh"
                                             , Vector( MeasurementValues( pastNow.toEpochMilli
                                                                        , Some(MultiType(MultiType.Value.Decimal("2091.258"))))))))

  "MeasurementReceiver" should {
    "process issue #11" in {
      val task  = client.process(issue_11)
      task.futureValue should be (MeasurementDataCnf(issue_11.messageId))
    }
  }
}
