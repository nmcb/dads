/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1


import scala.concurrent._

import akka.actor.typed._
import akka.http.scaladsl.model._
import akka.http.scaladsl._

import transport._
import transport.grpc.v1._

object MeasurementReceiver {

  def defaultMeasurementService(implicit actorSystem: ActorSystem[_]): MeasurementService =
    new MeasurementReceiver.DefaultMeasurementService(CounterRepository(DadsSettings()))

  class DefaultMeasurementService(repository: CounterRepository)(implicit system: ActorSystem[_])
    extends MeasurementService {

    import Codec._

    implicit val executionContext: ExecutionContext =
      system.executionContext

    def process(ind: MeasurementDataInd): Future[MeasurementDataCnf] = {
      val update = ind.as[Update]

      // FIXME currently only returns a cnf if all adjustments succeed
      Future.sequence(
        update.measurements
          .map(measurement => Adjustment(measurement.sourceId, measurement.timestamp, measurement.value))
          .map(adjustment  => repository.addToAll(adjustment)))
        .map(_ => MeasurementDataCnf(update.messageId))
    }
  }
}

class MeasurementReceiver(implicit system: ActorSystem[_]) {

  //  TODO System inbound boundary:
  //
  //  - Input validation
  //  - Input codec
  //  = Acknowledgement state (how principled do yo dare to discuss this?)
  //  - Drop inbound chain
  //  - Logging
  //  - Horizontal scaling: testing and non-functionals
  //  - Round-trip testing
  //  - Production testing


  import akka.actor.typed.scaladsl.adapter._

  def run(): Future[Http.ServerBinding] = {

    implicit val executionContext: ExecutionContext =
      system.executionContext

    val service: HttpRequest => Future[HttpResponse] =
      MeasurementServiceHandler(
        new MeasurementReceiver.DefaultMeasurementService(
          CounterRepository(DadsSettings())))

    Http()(system.toClassic).bindAndHandleAsync(
      service,
      interface = "127.0.0.1",
      port = 8080,
      connectionContext = HttpConnectionContext())
  }
}