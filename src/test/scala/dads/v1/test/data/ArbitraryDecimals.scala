/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package test
package data

import org.scalacheck._

trait ArbitraryDecimals
  extends ArbitrarySources
  with RealTime
{
  import Gen._
  import Arbitrary._

  import DadsSettings._

  import RealTimeDecimalRepository._

  implicit val arbitraryDecimal: Arbitrary[Decimal] =
    Arbitrary {
      for {
        sourceId <- arbitrary[SourceId]
        value <- choose(MinDecimalReadingValue, MaxDecimalReadingValue)
      } yield Decimal(sourceId, pastNow.withUncertainty, value)
    }
}
