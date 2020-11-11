/**
 * This is free and unencumbered software released into the public domain, still ...
 *
 * Copyright (C) 2020-11-10 - NMCB BV
 */

package dads.v1.aurum.test

import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors
import akka.grpc._
import dads.v1.test.data._
import dads.v1.transport.grpc.v2._
import org.scalacheck._

import scala.concurrent._
import scala.util._

object BartBot
  extends App
    with RealTime
    with ArbitraryRequests {

  import Arbitrary._

  val Host = "aurum-app-load-balancer-4747366a6255f45e.elb.eu-central-1.amazonaws.com"
  val Port = 8080

  implicit val clientSystem: ActorSystem[_] =
    ActorSystem(Behaviors.empty, "TestAWSMeasurementReceiver")

  implicit val ec: ExecutionContext =
    clientSystem.executionContext

  val client: MeasurementServiceClient =
    MeasurementServiceClient(
      GrpcClientSettings
        .connectToServiceAt(Host, Port)
        .withTls(false))

  val request: MeasurementDataInd =
    arbitrary[MeasurementDataInd].sample.getOrElse(throw new RuntimeException("booms"))

  println(s"Testing with new messages: ${request.messageId}-${request}")

  val task: Future[MeasurementDataCnf] =
    client.process(request)

  task.onComplete {
    case Success(response) =>
      println(s"Successfully processed: ${response.messageId}")
      clientSystem.terminate()
    case Failure(exception) =>
      println(s"Failure: ${exception.getMessage}")
      exception.printStackTrace()
      clientSystem.terminate()
  }
}
