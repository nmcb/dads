/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package transport

import java.time._

import cats.implicits._

import transport.grpc.v1._

import scala.util._

// TODO decouple validate/decode ?
trait Codec[M <: scalapb.GeneratedMessage, A] {
  def decode(msg: M): Val[A]
}

object Codec {

  import MeasurementReceiver._

  case object NoMessageIdError    extends InboundError("No valid (UUID) messageId")
  case object NoSourceIdError     extends InboundError("No valid (UUID) sourceId")
  case object NoUnitError         extends InboundError("No valid (kW) unit of measurement")
  case object NoValueError        extends InboundError("No (decimal) value present")
  case object NoPosValueError     extends InboundError("No positive (decimal) value")
  case object NoValidInstantError extends InboundError("No valid instant")
  case object NoSourcesError      extends InboundError("No measurements")
  case object TooManySourcesError extends InboundError(s"Too many (>$MaxSourceIdsPerMessage) measurements")
  case object NoValuesError       extends InboundError("No measurement values")
  case object TooManyValuesError  extends InboundError(s"Too many (>$MaxMeasurementValuesPerSourceId) measurement values")

  implicit class CodecOps[M <: scalapb.GeneratedMessage](msg: M) {

    def as[A : Codec[M,*]]: Val[A] =
      implicitly[Codec[M,A]].decode(msg)
  }

  def validateMessageId(messageId: String): Val[MessageId] =
    Try(MessageId.fromString(messageId)).fold(_ => NoMessageIdError.invalidNec, _.validNec)

  def validateSourceId(sourceId: String): Val[SourceId] =
    Try(SourceId.fromName(sourceId)).fold(_ => NoSourceIdError.invalidNec, _.validNec)

  def validateUnit(unit: String): Val[String] = {
    // TODO squants
    if (unit != "kW") NoUnitError.invalidNec else unit.validNec
  }

  def validateSeqNr(seqNr: Int): Val[Int] =
    seqNr.validNec // Ignored

  def validateInstant(instant: Long): Val[Instant] =
    Try(Instant.ofEpochMilli(instant)).fold(_ => NoValidInstantError.invalidNec, _.validNec)

  def validateMultiTypeValue(multiType: Option[MultiType]): Val[Long] =
    if (multiType.isEmpty || !multiType.get.value.isDecimal)
      NoValueError.invalidNec
    else
      Try(multiType.get.value.decimal.map(_.toLong).get)
        .fold( _ => NoValueError.invalidNec
             , l => if (l > 0) l.validNec else NoPosValueError.invalidNec
             )

  def validateMeasurementValuesList(values: List[MeasurementValues]): Val[Seq[(Instant,Long)]] =
    if (values.isEmpty)
      NoValuesError.invalidNec
    else if (values.size > MaxMeasurementValuesPerSourceId)
      TooManyValuesError.invalidNec
    else values.map( mv =>
      ( validateInstant(mv.timestamp)
      , validateMultiTypeValue(mv.value)
      ).mapN(tuple2Identity)
    ).sequence[Val,(Instant,Long)]

  def validateMeasurementData(measurementData: MeasurementData): Val[Seq[Measurement]] = {
    def rollout(sourceId: SourceId, unit: String)(values: Seq[(Instant, Long)]): Seq[Measurement] =
      values.map { case (instant, reading) =>
        Measurement(sourceId, instant, reading, unit)
      }

    ( validateSourceId(measurementData.sourceId)
    , validateUnit(measurementData.unit)
    , validateMeasurementValuesList(measurementData.data.toList)
    ).mapN { case (sourceId, unit, values) =>
        rollout(sourceId, unit)(values)
    }
  }

  def validateMeasurementDataSeq(data: List[MeasurementData]): Val[Seq[Measurement]] =
    if      (data.isEmpty)                        NoSourcesError.invalidNec
    else if (data.size > MaxSourceIdsPerMessage)  TooManySourcesError.invalidNec
    else data.map(md => validateMeasurementData(md)).sequence.map(_.flatten)

  implicit val measurementIndCodec: Codec[MeasurementDataInd, Update] =
    indication =>
      ( validateMessageId(indication.messageId)
      , validateMeasurementDataSeq(indication.measurements.toList)
      ).mapN(Update)
}
