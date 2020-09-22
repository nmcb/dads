/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1

import akka.Done
import akka.actor.CoordinatedShutdown

import scala.concurrent._
import akka.actor.typed._
import akka.http.scaladsl.model._
import akka.http.scaladsl._
import transport._
import grpc._

import scala.util.{Failure, Success}

object MeasurementReceiver {

  import v1.MeasurementDataCnf
  import v1.MeasurementDataInd
  import v1.MeasurementService

  def defaultMeasurementService(implicit actorSystem: ActorSystem[_]): MeasurementService =
    new MeasurementReceiver.DefaultMeasurementService(CounterRepository(DadsSettings()))

  class DefaultMeasurementService(repository: CounterRepository)(implicit system: ActorSystem[_])
    extends MeasurementService {

    import Codec._

    import CounterRepository._
    import CounterOn._

    val AllCountersOn: Seq[CounterOn] =
      Seq(HourByDayCounterOn, DayByMonthCounterOn, MonthByYearCounterOn, WeekByYearCounterOn, YearCounterOn)

    implicit val executionContext: ExecutionContext =
      system.executionContext

    private def processFor(counterOn: CounterOn)(measurement: Measurement): Future[Done] =
      for {
        current    <- repository.getFrom(counterOn)(measurement.sourceId)(measurement.timestamp)
        adjustment  = Adjustment(measurement.sourceId, measurement.timestamp, measurement.counterAdjustment - current)
        _          <- repository.addTo(counterOn)(adjustment)
      } yield Done

    private def processForAll(measurement: Measurement): Future[Done] =
      Future
        .sequence(AllCountersOn.map(counterOn => processFor(counterOn)(measurement)))
        .map(_ => Done)

    def process(ind: MeasurementDataInd): Future[MeasurementDataCnf] =
      // FIXME client protocol/interface, currently only returns a cnf if all adjustments succeed
      Future
        .sequence(ind.as[Update].measurements.map(measurement => processForAll(measurement)))
        .map(_ => MeasurementDataCnf(ind.messageId))
  }
}

class MeasurementReceiver(settings: DadsSettings.ReceiverSettings)(implicit system: ActorSystem[_]) {

  import v1.MeasurementServiceHandler

  //  TODO System inbound boundary:
  //
  //  - Input validation
  //  - Input codec
  //  = Acknowledgement state (how principled do you dare to discuss this?)
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
      MeasurementServiceHandler(MeasurementReceiver.defaultMeasurementService)

    val futureServerBinding: Future[Http.ServerBinding] =
      Http()(system.toClassic)
        .bindAndHandleAsync( handler           = service
                           , interface         = settings.host
                           , port              = settings.port
                           , connectionContext = HttpConnectionContext()
                           )

    futureServerBinding
      .map( binding =>
        binding
          .whenTerminationSignalIssued
          .map(deadline => {
            system.log.info(s"Stopping MeasurementReceiver at ${settings.host}:${settings.port} in ${deadline.time}")
            binding.terminate(deadline.time).onComplete { // FIXME terminate seems to be called twice
              case Success(termination) =>
                system.log.info(s"MeasurementReceiver at ${settings.host}:${settings.port} terminated")
              case Failure(exception) =>
                system.log.info(s"MeasurementReceiver at ${settings.host}:${settings.port} failed to terminate")
                system.log.error(s"Message: ${exception.getMessage}", exception)
                system.log.info(s"MeasurementReceiver at ${settings.host}:${settings.port} killed")
            }
          }))

    system.log.info(s"MeasurementReceiver running at ${settings.host}:${settings.port}")

    futureServerBinding
  }
}