/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1.test.data

import java.util._

import dads.v1.transport.grpc.v1._
import dads.v1.{DadsSettings, SourceId}
import org.scalacheck._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen.{choose, listOfN}

trait ArbitraryRequests
  extends RealTime
  with ArbitrarySources
{
  import DadsSettings._

  implicit val arbitraryMultiTypeValueDecimal: Arbitrary[MultiType.Value.Decimal] =
    Arbitrary(
      choose(MinDecimalReadingValue, MaxDecimalReadingValue)
        .map(_.toString)
        .map(MultiType.Value.Decimal(_)))

  implicit val arbitraryMultiTypeValue: Arbitrary[MultiType.Value] =
  // FIXME unused bool, string and bytes type
    Arbitrary(arbitrary[MultiType.Value.Decimal])

  implicit val arbitraryMultiType: Arbitrary[MultiType] =
    Arbitrary(arbitrary[MultiType.Value].map(MultiType.of))

  implicit val arbitraryMeasurementValues: Arbitrary[MeasurementValues] =
    Arbitrary {
      for {
        timestamp <- explicitArbitraryPastNowInstant.arbitrary
        value <- arbitrary[MultiType]
      } yield MeasurementValues.of(timestamp.toEpochMilli, Some(value))
    }

  implicit val arbitraryMeasurementData: Arbitrary[MeasurementData] =
    Arbitrary {
      for {
        sourceId <- arbitrary[SourceId].map(_.toString)
        sequenceNr = 0 // FIXME unused
        unitOfMeasurement = "kW" // FIXME unused
        size <- choose(1, MaxMeasurementsPerSourceId)
        data <- listOfN(size, arbitrary[MeasurementValues])
      } yield MeasurementData.of(sourceId, sequenceNr, unitOfMeasurement, data)
    }

  implicit val arbitraryMeasurementDataInd: Arbitrary[MeasurementDataInd] =
    Arbitrary {
      for {
        messageId <- arbitrary[UUID].map(_.toString)
        device = None // FIXME unused
        size <- choose(1, MaxSourceIdsPerIndication)
        measurements <- listOfN(size, arbitrary[MeasurementData])
      } yield MeasurementDataInd.of(messageId, device, measurements)
    }
}