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

  case object NoMessageIdError    extends InboundError("Has no valid (UUID) messageId")
  case object NoSourceIdError     extends InboundError("Has no valid (UUID) sourceId")
  case object NoUnitError         extends InboundError("Has no valid unit of measurement")
  case object NoValueError        extends InboundError("Has no valid (decimal) value")
  case object NoValidInstant      extends InboundError("Has no valid instant")
  case object NoMeasurements      extends InboundError("Contains no measurements")
  case object TooManyMeasurements extends InboundError(s"Contains too many (>$MaxMeasurementDataSize) measurements")

  implicit class DecoderOps[M <: scalapb.GeneratedMessage](msg: M) {

    def as[A: Decoder[M, *]]: ValidatedNec[InboundError,A] =
      implicitly[Decoder[M, A]].decode(msg)
  }

  def validateMessageId(messageId: String): ValidatedNec[InboundError,UUID] =
    Try(UUID.fromString(messageId)).fold(_ => NoMessageIdError.invalidNec, _.validNec)

  def validateSourceId(sourceId: String): ValidatedNec[InboundError,UUID] =
    Try(UUID.fromString(sourceId)).fold(_ => NoSourceIdError.invalidNec, _.validNec)

  def validateUnit(unit: String): ValidatedNec[InboundError,String] =
    if (unit != "kW") NoUnitError.invalidNec else unit.validNec

  def validateSeqNr(seqNr: Int): ValidatedNec[InboundError,Int] =
    seqNr.validNec // Ignored

  def validateInstant(instant: Long): ValidatedNec[InboundError,Instant] =
    Try(Instant.ofEpochMilli(instant)).fold(_ => NoValidInstant.invalidNec, _.validNec)

  def validateMultiTypeValue(multiType: Option[MultiType]): ValidatedNec[InboundError,Long] =
    if (multiType.isEmpty || !multiType.get.value.isDecimal) NoValueError.invalidNec
    else Try(multiType.get.value.decimal.map(_.toLong).get).fold(_ => NoValueError.invalidNec, _.validNec)

  def validateMeasurementDataNonEmpty(data: Seq[MeasurementData]): ValidatedNec[InboundError, Seq[MeasurementData]] =
    if (data.isEmpty) NoMeasurements.invalidNec else data.validNec

  def validateMeasurementDataInBounds(data: Seq[MeasurementData]): ValidatedNec[InboundError, Seq[MeasurementData]] =
    if (data.size > MaxMeasurementDataSize) TooManyMeasurements.invalidNec else data.validNec

  def validateMeasurementValuesSeqWith(values: List[MeasurementValues]): ValidatedNec[InboundError,Seq[(Instant,Long)]] = {
    val id: (Instant,Long) => (Instant,Long) =
      { case (instant, reading) => (instant, reading) }

    values.map(mv =>
      ( validateInstant(mv.timestamp)
      , validateMultiTypeValue(mv.value)
      ).mapN(id)
    ).sequence[ValidatedNec[InboundError, *], (Instant, Long)]
  }

  def validateMeasurementData(measurementData: MeasurementData): ValidatedNec[InboundError,Seq[Measurement]] =
    ( validateSourceId(measurementData.sourceId)
    , validateUnit(measurementData.unit)
    , validateMeasurementValuesSeqWith(measurementData.data.toList)
    ).mapN({ case (sourceId, unit, values) =>
        values.map({ case (instant, reading) =>
          Measurement(sourceId, instant, reading, unit)
        })
    })

  def validateMeasurementDataSeq(data: List[MeasurementData]): ValidatedNec[InboundError,Seq[Measurement]] =
    if (data.isEmpty) NoMeasurements.invalidNec
    else if (data.size > MaxMeasurementDataSize) TooManyMeasurements.invalidNec
    else data.map(md => validateMeasurementData(md)).sequence.map(_.flatten)


  implicit val measurementIndDecoder: Decoder[MeasurementDataInd, Update] =
    indication =>
      ( validateMessageId(indication.messageId)
      , validateMeasurementDataSeq(indication.measurements.toList)
      ).mapN(Update)
}
