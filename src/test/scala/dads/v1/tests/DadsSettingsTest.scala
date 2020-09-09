/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package tests

import org.scalatest._
import org.scalatest.matchers.should._

class DadsSettingsTest
  extends flatspec.AnyFlatSpec
    with Matchers {

  behavior of "DadsSettings"

  it should "read settings from the local (test) application.conf" in {
    val settings = DadsSettings()

    settings.realtimeKeyspace   shouldBe "dads_v1"
    settings.cumulativeKeyspace shouldBe "aurum"
  }
}
