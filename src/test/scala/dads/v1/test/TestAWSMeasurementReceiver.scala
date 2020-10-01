/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1.test

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.grpc.GrpcClientSettings
import dads.v1.test.data.MeasurementReceiverData
import dads.v1.{DadsSettings, RealTime}
import dads.v1.transport.grpc.v1.{MeasurementDataCnf, MeasurementDataInd, MeasurementServiceClient}
import org.scalacheck.Arbitrary.arbitrary

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object TestAWSMeasurementReceiver extends App with RealTime with MeasurementReceiverData {

  import ArbitraryRequests._

  val Host = "aurum-app-load-balancer-4747366a6255f45e.elb.eu-central-1.amazonaws.com"
  val Port = 8080

  implicit val clientSystem: ActorSystem[_] =
    ActorSystem(Behaviors.empty, "MeasurementServiceClient")

  implicit val ec: ExecutionContext =
    clientSystem.executionContext

  val client: MeasurementServiceClient =
    MeasurementServiceClient(
      GrpcClientSettings
        .connectToServiceAt(Host, Port)
        .withTls(false))

  val indication: MeasurementDataInd =
    arbitrary[MeasurementDataInd].sample.getOrElse(throw new RuntimeException("booms"))

  println(s"Testing with message: ${indication.messageId}")
  val task: Future[MeasurementDataCnf] =
    client.process(indication)

  task.onComplete {
    case Success(confirmation) =>
      println(s"Successfully processed: ${confirmation.messageId}")
      clientSystem.terminate()
    case Failure(exception) =>
      println(s"Failure: ${exception.getMessage}")
      exception.printStackTrace()
      clientSystem.terminate()
  }
}
