/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package test
package data

import java.time._
import java.time.temporal._
import java.util._
import org.scalacheck._


trait MeasurementReceiverData {

  import transport._

  import Instant._
  import ChronoUnit._
  import Arbitrary._
  import Gen._

  object ArbitraryRequests {

    import grpc.v1._

    val InstantSpread = 5 * YEARS.getDuration.dividedBy(2).toMillis
    val MaxMeasurementsPerMeasurementData = 5 // 1024
    val MaxMeasurementDataPerIndication   = 5 // 1024

    val MinDecimalValueMeasurement = 1L
    val MaxDecimalValueMeasurement = 1000L


    implicit val arbitraryInstant: Arbitrary[Instant] =
      Arbitrary {
        val start = EPOCH.toEpochMilli
        val end   = now.toEpochMilli + InstantSpread
        choose(start, end).map(Instant.ofEpochMilli)
      }

    implicit val arbitraryMultiTypeValueDecimal: Arbitrary[MultiType.Value.Decimal] =
      Arbitrary(
        choose(MinDecimalValueMeasurement, MaxMeasurementDataPerIndication)
          .map(_.toString)
          .map(MultiType.Value.Decimal(_)))

    implicit val arbitraryMultiTypeValue: Arbitrary[MultiType.Value] =
      Arbitrary(arbitrary[MultiType.Value.Decimal]) // FIXME unused bool, string and bytes type

    implicit val arbitraryMultiType: Arbitrary[MultiType] =
      Arbitrary(arbitrary[MultiType.Value].map(MultiType.of))

    implicit val arbitraryMeasurementValues: Arbitrary[MeasurementValues] =
      Arbitrary {
        for {
          timestamp <- arbitrary[Instant].map(_.toEpochMilli)
          value     <- arbitrary[MultiType].map(Some(_))
        } yield MeasurementValues.of(timestamp, value)
      }

    implicit val arbitraryMeasurementData: Arbitrary[MeasurementData] =
      Arbitrary {
        for {
          sourceId          <- arbitrary[UUID].map(_.toString)
          sequenceNr        =  0    // FIXME unused
          unitOfMeasurement =  "kW" // FIXME unused
          size              <- choose(1, MaxMeasurementsPerMeasurementData)
          data              <- listOfN(size, arbitrary[MeasurementValues])
        } yield MeasurementData.of(sourceId, sequenceNr, unitOfMeasurement, data)
      }

    implicit val arbitraryMeasurementDataInd: Arbitrary[MeasurementDataInd] =
      Arbitrary {
        for {
          messageId    <- arbitrary[UUID].map(_.toString)
          device       =  None // FIXME unused
          size         <- choose(1, MaxMeasurementDataPerIndication)
          measurements <- listOfN(size, arbitrary[MeasurementData])
        } yield MeasurementDataInd.of(messageId, device, measurements)
      }
  }
}
