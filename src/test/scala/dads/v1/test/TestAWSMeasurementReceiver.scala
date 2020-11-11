/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package test

import scala.concurrent._

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.grpc._

import org.scalacheck._

import scala.util._

import data._
import transport._
import grpc.v2._

object TestAWSMeasurementReceiver
  extends App
    with RealTime
    with ArbitraryRequests
{
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
