/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package test
package data

import dads.v1.transport.Codec
import org.scalacheck._

import squants.energy._

trait ArbitraryPower
  extends ArbitrarySources
  with RealTime
{
  import Gen._
  import Arbitrary._

  import DadsSettings._

  import RealTimePowerRepository._

  implicit val arbitraryDecimal: Arbitrary[PowerAttribution] =
    Arbitrary {
      for {
        sourceId <- arbitrary[SourceId]
        value <- choose(MinDecimalReadingValue, MaxDecimalReadingValue)
        unit  <- oneOf(Codec.PowerUnits)
        power <- Gen.const(Power.parseTuple((value, unit))) suchThat(_.isSuccess)
      } yield PowerAttribution(sourceId, pastNow.withUncertainty, power.get)
    }
}
