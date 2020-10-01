/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1.test

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.grpc.GrpcClientSettings
import dads.v1.test.data.MeasurementReceiverData
import dads.v1.{DadsSettings, RealTime}
import dads.v1.transport.grpc.v1.{MeasurementDataInd, MeasurementServiceClient}
import org.scalacheck.Arbitrary.arbitrary

import scala.util.{Failure, Success}

object TestMain extends App with RealTime with MeasurementReceiverData {

  import ArbitraryRequests._

  val settings: DadsSettings =
    DadsSettings()

  implicit val clientSystem: ActorSystem[_] =
    ActorSystem(Behaviors.empty, "MeasurementServiceClient")

  val client: MeasurementServiceClient =
    MeasurementServiceClient(
      GrpcClientSettings
        .connectToServiceAt("aurum-app-load-balancer-4747366a6255f45e.elb.eu-central-1.amazonaws.com", settings.measurementReceiver.port)
        .withTls(false)) // FIXME should not be used in production

  val ind = arbitrary[MeasurementDataInd].sample.getOrElse(throw new RuntimeException("booms"))
  println(ind.toProtoString)
  val task  = client.process(ind)

  implicit val ec = clientSystem.executionContext

  task.onComplete {
    case Success(res) =>
      println(s"WHATEVER: $res")
      clientSystem.terminate()
    case Failure(exception) =>
      exception.printStackTrace()
      clientSystem.terminate()
  }

}
