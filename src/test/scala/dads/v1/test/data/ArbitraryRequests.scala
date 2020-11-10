/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package test
package data

import java.util._

import org.scalacheck._

trait ArbitraryRequests
  extends ArbitrarySources
  with RealTime
{
  import Gen._
  import Arbitrary._
  import DadsSettings._

  import transport._
  import grpc.v2._

  implicit val arbitraryMultiTypeValueDecimal: Arbitrary[MultiType.Value.Decimal] =
    Arbitrary(
      choose(MinDecimalReadingValue, MaxDecimalReadingValue)
        .map(_.toString)
        .map(MultiType.Value.Decimal(_)))

  implicit val arbitraryMultiTypeValue: Arbitrary[MultiType.Value] =
    Arbitrary(arbitrary[MultiType.Value.Decimal]) // FIXME unused bool, string and bytes type

  implicit val arbitraryMultiType: Arbitrary[MultiType] =
    Arbitrary(arbitrary[MultiType.Value].map(MultiType.of))

  implicit val arbitraryMeasurementValues: Arbitrary[MeasurementValues] =
    Arbitrary {
      for {
        timestamp <- explicitArbitraryPastNowInstant.arbitrary
        value     <- arbitrary[MultiType]
      } yield MeasurementValues.of(timestamp.toEpochMilli, Some(value))
    }

  implicit val arbitraryMeasurementData: Arbitrary[MeasurementData] =
    Arbitrary {
      for {
        sourceId          <- arbitrary[SourceId].map(_.toString)
        sequenceNr        =  0    // FIXME unused
        unitOfMeasurement =  "kW" // FIXME unused
        size              <- choose(1, MaxMeasurementsPerSourceId)
        data              <- listOfN(size, arbitrary[MeasurementValues])
      } yield MeasurementData.of(sourceId, sequenceNr, unitOfMeasurement, data)
    }

  implicit val arbitraryMeasurementDataReq: Arbitrary[MeasurementDataReq] =
    Arbitrary {
      for {
        messageId    <- arbitrary[UUID].map(_.toString)
        device       =  None  // FIXME unused
        size         <- choose(1, MaxSourceIdsPerIndication)
        measurements <- listOfN(size, arbitrary[MeasurementData])
      } yield MeasurementDataReq.of(messageId, device, measurements)
    }
}
