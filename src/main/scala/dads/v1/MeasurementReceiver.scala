/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1

import scala.util._

import scala.concurrent._

import akka.Done
import akka.actor.typed._
import akka.http.scaladsl.model._
import akka.http.scaladsl._

import transport._
import grpc.v1._

object MeasurementReceiver {

  final val MaxSourceIdsPerMessage          = 5
  final val MaxMeasurementValuesPerSourceId = 5

  class DefaultMeasurementService(counterRepository: CounterRepository, realTimeRepository: RealTimeDecimalRepository)(implicit system: ActorSystem[_])
    extends MeasurementService {

    import transport.Codec._

    import CounterRepository._
    import CounterOn._

    val AllCountersOn: Seq[CounterOn] =
      Seq(HourByDayCounterOn, DayByMonthCounterOn, MonthByYearCounterOn, WeekByYearCounterOn, YearCounterOn)

    implicit val executionContext: ExecutionContext =
      system.executionContext

    private def processFor(counterOn: CounterOn)(measurement: Measurement): Future[Done] =
      for {

        // 1) FIXME realTimeRepository (cache) should return measurement.reading instead of counter.adjustment
        current    <- counterRepository.getFrom(counterOn)(measurement.sourceId)(measurement.timestamp)

        // 2) FIXME update realTimeRepository (cache plus cassandra) [SourceId,Measurement]
        // - not when new instant <= old instant (filtering)
        // - reading <= 0
        // - current == 0  => break;

        // 3) FIXME calculate adjustment = measurement.reading - current
        // - convert measurement unit to adjustment unit
        adjustment =  Adjustment(measurement.sourceId, measurement.timestamp, measurement.reading - current)

        // 4) FIXME counterRepository
        // - adjustment <= 0 do no update
        // - adjustment >= max TODO Bart
        _          <- counterRepository.addTo(counterOn)(adjustment)
      } yield Done

    private def processForAll(measurement: Measurement): Future[Done] =
      Future
        .sequence(AllCountersOn.map(counterOn => processFor(counterOn)(measurement)))
        .map(_ => Done)

    def process(inbound: MeasurementDataInd): Future[MeasurementDataCnf] =
    // FIXME client protocol/interface, currently only returns a cnf if all adjustments succeed
      inbound
        .as[Update]
        .fold( errors => throw new RuntimeException(s"Boom: $errors")
             , update => Future
                           .sequence(update.measurements.map(measurement => processForAll(measurement)))
                           .map(_ => MeasurementDataCnf(update.messageId.toString)))
  }
}

class MeasurementReceiver(settings: DadsSettings.ReceiverSettings, counterRepository: CounterRepository, realTimeRepository: RealTimeDecimalRepository)(implicit system: ActorSystem[_]) {

  //  TODO system inbound boundary:
  //
  //  x Input validation
  //  x Input codec
  //  - Acknowledgement state (how principled do you dare to discuss this?)
  //  x Drop inbound chain
  //  - Logging
  //  - Horizontal scaling: testing and non-functionals
  //  - Round-trip testing
  //  - Production testing

  import akka.actor.typed.scaladsl.adapter._
  import MeasurementReceiver._

  def run(): Future[Http.ServerBinding] = {

    implicit val executionContext: ExecutionContext =
      system.executionContext

    val handler: HttpRequest => Future[HttpResponse] =
      MeasurementServiceHandler(new DefaultMeasurementService(counterRepository, realTimeRepository))

    val futureServerBinding: Future[Http.ServerBinding] =
      Http()(system.toClassic)
        .bindAndHandleAsync( handler           = handler
                           , interface         = settings.host
                           , port              = settings.port
                           , connectionContext = HttpConnectionContext()
                           )

    futureServerBinding
      .map( binding =>
        binding
          .whenTerminated
          .onComplete {
            case Success(_) =>
              system.log.info(s"MeasurementReceiver at ${settings.host}:${settings.port} terminated")
            case Failure(exception) =>
              system.log.error(s"MeasurementReceiver at ${settings.host}:${settings.port} failed to terminate")
              system.log.error(s"Message: ${exception.getMessage}", exception)
          })

    system.log.info(s"MeasurementReceiver running at ${settings.host}:${settings.port}")

    futureServerBinding
  }
}