/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package test

import com.typesafe.config.ConfigFactory
import org.scalatest.flatspec._
import org.scalatest.matchers.should._

class DadsSettingsTest
  extends AnyFlatSpec
    with Matchers {

  behavior of "DadsSettings"

  it should "read all settings from provided (fake) application-test-dads.conf" in {
    val settings: DadsSettings =
      DadsSettings(
        ConfigFactory
          .load("application-test-dads")
          .getConfig("dads"))

    settings.realtimeKeyspace  shouldBe "realtimeKeyspace"
    settings.counterKeyspace   shouldBe "counterKeyspace"

    settings.measurementReceiver.host shouldBe "host"
    settings.measurementReceiver.port shouldBe  1234
  }

  it should "read runtime settings from default (main) application.conf" in {
    val settings = DadsSettings()

    settings.realtimeKeyspace  shouldBe "dads_v1"
    settings.counterKeyspace   shouldBe "aurum"

    settings.measurementReceiver.host shouldBe "127.0.0.1"
    settings.measurementReceiver.port shouldBe 8080
  }
}
