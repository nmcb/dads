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

  import DadsSettings._

  case object NoMessageId          extends InboundError("No valid (UUID) messageId")
  case object NoSourceId           extends InboundError("No valid (UUID) sourceId")
  case object NoUnitOfMeasurement  extends InboundError("No valid (kW) unit of measurement")
  case object NoDecimalValue       extends InboundError("No (decimal) value present")
  case object NotPositive          extends InboundError("No positive (decimal) value")
  case object NoValidInstant       extends InboundError("No valid instant")
  case object NoSources            extends InboundError("No measurements")
  case object TooManySources       extends InboundError(s"Too many (>$MaxSourceIdsPerIndication) sources")
  case object NoMeasurements       extends InboundError("No measurement values")
  case object TooManyMeasurements  extends InboundError(s"Too many (>$MaxMeasurementsPerSourceId) measurements")

  implicit class CodecOps[M <: scalapb.GeneratedMessage](msg: M) {

    def as[A : Codec[M,*]]: Val[A] =
      implicitly[Codec[M,A]].decode(msg)
  }

  def validateMessageId(messageId: String): Val[MessageId] =
    Try(MessageId.fromString(messageId)).fold(_ => NoMessageId.invalidNec, _.validNec)

  def validateSourceId(sourceId: String): Val[SourceId] =
    Try(SourceId.fromName(sourceId)).fold(_ => NoSourceId.invalidNec, _.validNec)

  def validateUnit(unit: String): Val[String] = {
    // TODO squants
    if (unit != "kW") NoUnitOfMeasurement.invalidNec else unit.validNec
  }

  def validateSeqNr(seqNr: Int): Val[Int] =
    seqNr.validNec // Ignored

  def validateInstant(instant: Long): Val[Instant] =
    Try(Instant.ofEpochMilli(instant)).fold(_ => NoValidInstant.invalidNec, _.validNec)

  def validateMultiTypeValue(multiType: Option[MultiType]): Val[Long] =
    if (multiType.isEmpty || !multiType.get.value.isDecimal)
      NoDecimalValue.invalidNec
    else
      Try(multiType.get.value.decimal.map(_.toLong).get)
        .fold( _ => NoDecimalValue.invalidNec
             , l => if (l > 0) l.validNec else NotPositive.invalidNec
             )

  def validateMeasurementValuesList(measurements: List[MeasurementValues]): Val[Seq[(Instant,Long)]] =
    if (measurements.isEmpty)
      NoMeasurements.invalidNec
    else if (measurements.size > MaxMeasurementsPerSourceId)
      TooManyMeasurements.invalidNec
    else measurements.map(mv =>
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
    if (data.isEmpty)
      NoSources.invalidNec
    else if (data.size > MaxSourceIdsPerIndication)
      TooManySources.invalidNec
    else
      data.map(md => validateMeasurementData(md)).sequence.map(_.flatten)

  implicit val measurementIndCodec: Codec[MeasurementDataInd, Update] =
    indication =>
      ( validateMessageId(indication.messageId)
      , validateMeasurementDataSeq(indication.measurements.toList)
      ).mapN(Update)
}
