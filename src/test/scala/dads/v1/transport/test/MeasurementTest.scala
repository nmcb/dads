/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package transport
package test

import java.time._
import java.util._

import org.scalatest.flatspec._
import org.scalatest.matchers.should._

class MeasurementTest
  extends AnyFlatSpec
    with Matchers {

  def newFixture(value: Double, unit: NaturalUnit): Measurement =
    Measurement( SourceId.fromName(UUID.randomUUID().toString)
               , Instant.now()
               , value
               , unit
               )

  behavior of "Measurement"

  it should "normalise kWh readings" in {
    newFixture(1.0 , "kWh").normalise.unit    shouldBe "μWh"
    newFixture(1.0 , "kWh").normalise.reading shouldBe 1.0E9
  }

  it should "normalise m³ readings" in {
    newFixture(1.0 , "m³").normalise.unit    shouldBe "mm³"
    newFixture(1.0 , "m³").normalise.reading shouldBe 1.0E9
  }
}
