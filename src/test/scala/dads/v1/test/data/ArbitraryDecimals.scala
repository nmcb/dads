/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1.test.data

import dads.v1.DadsSettings._
import dads.v1.{RealTimeDecimalRepository, SourceId}
import org.scalacheck._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen.choose

trait ArbitraryDecimals
  extends RealTime
  with ArbitrarySources
{
  import RealTimeDecimalRepository._

  implicit val arbitraryDecimal: Arbitrary[Decimal] =
    Arbitrary {
      for {
        sourceId <- arbitrary[SourceId]
        value <- choose(MinDecimalReadingValue, MaxDecimalReadingValue)
      } yield Decimal(sourceId, pastNow.withUncertainty, value)
    }
}
