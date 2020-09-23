/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package transport

import java.time._
import java.util.UUID

import cats.data._
import cats.implicits._

import transport.grpc.v1._

import scala.util._

object Codec {

  import MeasurementReceiver._

  case object NoMessageIdError    extends InboundError("No valid (UUID) messageId")
  case object NoSourceIdError     extends InboundError("No valid (UUID) sourceId")
  case object NoUnitError         extends InboundError("No valid unit of measurement")
  case object NoValidValueError   extends InboundError("No valid (decimal) value")
  case object NoValidInstantError extends InboundError("No valid instant")
  case object NoSourcesError      extends InboundError("No measurements")
  case object TooManySourcesError extends InboundError(s"Too many (>$MaxSourceIdsPerMessage) measurements")
  case object NoValuesError       extends InboundError("No measurement values")
  case object TooManyValuesError  extends InboundError(s"Too many (>$MaxMeasurementValuesPerSourceId) measurement values")

  implicit class DecoderOps[M <: scalapb.GeneratedMessage](msg: M) {

    def as[A: Decoder[M,*]]: ValidatedNec[InboundError,A] =
      implicitly[Decoder[M, A]].decode(msg)
  }

  def validateMessageId(messageId: String): ValidatedNec[InboundError,UUID] =
    Try(UUID.fromString(messageId)).fold(_ => NoMessageIdError.invalidNec, _.validNec)

  def validateSourceId(sourceId: String): ValidatedNec[InboundError,UUID] =
    Try(UUID.fromString(sourceId)).fold(_ => NoSourceIdError.invalidNec, _.validNec)

  def validateUnit(unit: String): ValidatedNec[InboundError,String] = {
    // TODO squants
    if (unit != "kW") NoUnitError.invalidNec else unit.validNec
  }

  def validateSeqNr(seqNr: Int): ValidatedNec[InboundError,Int] =
    seqNr.validNec // Ignored

  def validateInstant(instant: Long): ValidatedNec[InboundError,Instant] =
    Try(Instant.ofEpochMilli(instant)).fold(_ => NoValidInstantError.invalidNec, _.validNec)

  def validateMultiTypeValue(multiType: Option[MultiType]): ValidatedNec[InboundError,Long] =
    if (multiType.isEmpty || !multiType.get.value.isDecimal) NoValidValueError.invalidNec
    else Try(multiType.get.value.decimal.map(_.toLong).get).fold(_ => NoValidValueError.invalidNec, _.validNec)

  def validateMeasurementValuesList(values: List[MeasurementValues]): ValidatedNec[InboundError,Seq[(Instant,Long)]] = {
    val id: (Instant,Long) => (Instant,Long) =
      { case (instant, reading) => (instant, reading) }

    if (values.isEmpty)
      NoValuesError.invalidNec
    else if (values.size > MaxMeasurementValuesPerSourceId)
      TooManyValuesError.invalidNec
    else values.map( mv =>
      ( validateInstant(mv.timestamp)
      , validateMultiTypeValue(mv.value)
      ).mapN(id)
    ).sequence[ValidatedNec[InboundError,*],(Instant,Long)]
  }

  def validateMeasurementData(measurementData: MeasurementData): ValidatedNec[InboundError,Seq[Measurement]] =
    ( validateSourceId(measurementData.sourceId)
    , validateUnit(measurementData.unit)
    , validateMeasurementValuesList(measurementData.data.toList)
    ).mapN({ case (sourceId, unit, values) =>
        values.map({ case (instant, reading) =>
          Measurement(sourceId, instant, reading, unit)
        })
    })

  def validateMeasurementDataSeq(data: List[MeasurementData]): ValidatedNec[InboundError,Seq[Measurement]] =
    if      (data.isEmpty)                        NoSourcesError.invalidNec
    else if (data.size > MaxSourceIdsPerMessage)  TooManySourcesError.invalidNec
    else data.map(md => validateMeasurementData(md)).sequence.map(_.flatten)

  implicit val measurementIndDecoder: Decoder[MeasurementDataInd, Update] =
    indication =>
      ( validateMessageId(indication.messageId)
      , validateMeasurementDataSeq(indication.measurements.toList)
      ).mapN(Update)
}
