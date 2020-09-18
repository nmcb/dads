/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package test

import com.typesafe.config.ConfigFactory
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.grpc.GrpcClientSettings
import dads.v1.transport.grpc.v1.{MeasurementDataCnf, MeasurementDataInd, MeasurementServiceClient}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MeasurementReceiverTest
  extends AnyWordSpec
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures
    with RealWorld {

  val testKit = ActorTestKit(ConfigFactory.defaultApplication())

  val settings = DadsSettings().measurementReceiver

  val bound = new MeasurementReceiver(settings)(testKit.system).run()

  val serverBinding = bound.futureValue

  implicit val clientSystem: ActorSystem[_] =
    ActorSystem(Behaviors.empty, "MeasurementServiceClient")

  override protected def afterAll(): Unit = {
    super.afterAll()
    clientSystem.terminate()
    serverBinding.terminate(Main.RealTimeServiceLevelAgreement)
  }

  val client: MeasurementServiceClient =
    MeasurementServiceClient(
      GrpcClientSettings
        .connectToServiceAt(settings.host, settings.port)
        .withTls(false))

  "MeasurementReceiver" should {
    "reply to single request" in {
      val reply = client.process(MeasurementDataInd())
      reply.futureValue should be (MeasurementDataCnf.defaultInstance)
    }
  }


}

/*

//#full-example
package com.example.helloworld

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.grpc.GrpcClientSettings

import com.typesafe.config.ConfigFactory

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

class GreeterSpec
  extends AnyWordSpec
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  implicit val patience: PatienceConfig = PatienceConfig(scaled(5.seconds), scaled(100.millis))

  // important to enable HTTP/2 in server ActorSystem's config
  val conf = ConfigFactory.parseString("akka.http.server.preview.enable-http2 = on")
    .withFallback(ConfigFactory.defaultApplication())

  val testKit = ActorTestKit(conf)

  val serverSystem: ActorSystem[_] = testKit.system
  val bound = new GreeterServer(serverSystem).run()

  // make sure server is bound before using client
  bound.futureValue

  implicit val clientSystem: ActorSystem[_] = ActorSystem(Behaviors.empty, "GreeterClient")

  val client =
    GreeterServiceClient(GrpcClientSettings.fromConfig("helloworld.GreeterService"))

  override def afterAll: Unit = {
    ActorTestKit.shutdown(clientSystem)
    testKit.shutdownTestKit()
  }

  "GreeterService" should {
    "reply to single request" in {
      val reply = client.sayHello(HelloRequest("Alice"))
      reply.futureValue should ===(HelloReply("Hello, Alice"))
    }
  }
}
//#full-example

 */