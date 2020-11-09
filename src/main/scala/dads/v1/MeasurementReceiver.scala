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

  class DefaultMeasurementService( counterRepository  : CounterRepository
                                 , realTimeRepository : RealTimeDecimalRepository
  )(
    implicit system: ActorSystem[_]
   )
    extends MeasurementService {

    import transport._
    import Codec._

    import CounterRepository._
    import CounterOn._
    import RealTimeDecimalRepository._

    val AllCountersOn: Seq[CounterOn] =
      Seq( HourByDayCounterOn
         , DayByMonthCounterOn
         , MonthByYearCounterOn
         , WeekByYearCounterOn
         , YearCounterOn
         )

    implicit val executionContext: ExecutionContext =
      system.executionContext

    private def process(measurement: Measurement): Future[Done] = {

      def isNewMeasurement(current: Option[Decimal], measurement: Measurement): Boolean = {
        val res = current.exists(decimal => decimal.instant.isBefore(measurement.timestamp)) || (current.isEmpty && measurement.reading >= 0)
        system.log.info(s"${measurement.sourceId} => new measurement [${res}]")
        res
      }

      def isIncreasing(current: Option[Decimal], measurement: Measurement): Boolean =
        current.map(decimal => decimal.value < measurement.reading).getOrElse(true)

      def adjustmentFor(current: Option[Decimal], measurement: Measurement): Adjustment = {
        current match {
          case None =>
            Adjustment( measurement.sourceId
                      , measurement.timestamp
                      , measurement.reading
                      )
          case Some(currentDecimal) =>
            Adjustment( measurement.sourceId
                      , measurement.timestamp
                      , currentDecimal.value - measurement.reading
                      )
        }
      }

      for {
        _         <- Future.successful(system.log.info(s"${measurement.sourceId} => processing measurement: $measurement"))
        current   <- realTimeRepository.getLast(measurement.sourceId)
        _         <- Future.successful(system.log.info(s"${measurement.sourceId} => current value: $current"))
        persisted <- Future
                       .successful(isNewMeasurement(current, measurement))
                       .flatMap(newMeasurement =>
                         if (newMeasurement)
                           realTimeRepository.set(Decimal.from(measurement)).map(_ => true)
                         else
                           Future.successful(false)
                       )
        _         <- Future.successful(system.log.info(s"${measurement.sourceId} => realtime persisted: $persisted"))
        result    <- Future
                       .successful(persisted)
                       .flatMap[Done](add =>
                         if (add && isIncreasing(current, measurement))
                           Future.sequence(
                             AllCountersOn.map(counterOn =>
                               counterRepository.addTo(counterOn)(adjustmentFor(current, measurement))
                           )).map(_ => {
                             system.log.info(s"${measurement.sourceId} => value added to counters")
                           }).map(toDone)
                         else
                           Future.successful(Done)).map(_ => {
                              system.log.info(s"${measurement.sourceId} => value not added to counters")
                           }).map(toDone)
        _         <- Future.successful(system.log.info(s"${measurement.sourceId} => processed: $result"))
      } yield result
    }

    def process(inbound: MeasurementDataInd): Future[MeasurementDataCnf] =
    // FIXME client protocol/interface, currently only returns a cnf if all adjustments succeed
      inbound
        .as[Update]
        .fold( errors => throw new RuntimeException(s"boom: $errors")
             , update => Future
                           .sequence(update.measurements.map(measurement => process(measurement)))
                           .map(_ => MeasurementDataCnf(update.messageId.toString)))

    private val toDone: Any => Done =
      _ => Done
  }
}

class MeasurementReceiver(settings: DadsSettings.ReceiverSettings, counterRepository: CounterRepository, realTimeRepository: RealTimeDecimalRepository)(implicit system: ActorSystem[_]) {

  //  TODO system inbound boundary:
  //
  //  x Input validation
  //  x Input codec
  //  x Acknowledgement state (how principled do you dare to discuss this?) [math guarantees]
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