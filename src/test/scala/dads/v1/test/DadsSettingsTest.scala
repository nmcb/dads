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
      new DadsSettings(
        ConfigFactory
          .load("application-test-dads")
          .getConfig("dads"))

    settings.repositorySettings.realtimeKeyspace  shouldBe "realtimeKeyspace"
    settings.repositorySettings.counterKeyspace   shouldBe "counterKeyspace"

    settings.measurementReceiver.host shouldBe "host"
    settings.measurementReceiver.port shouldBe  1234
  }

  it should "read runtime settings from default (main) application.conf" in {
    val settings = new DadsSettings()

    settings.repositorySettings.realtimeKeyspace  shouldBe "dads_v1"
    settings.repositorySettings.counterKeyspace   shouldBe "aurum"

    settings.measurementReceiver.host shouldBe "0.0.0.0"
    settings.measurementReceiver.port shouldBe 8080
  }
}
