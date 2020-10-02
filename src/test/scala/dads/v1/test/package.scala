/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package test

import com.typesafe.config._

class DadsTestSettings(config: Config =
       ConfigFactory.defaultApplication.getConfig("dads"))
  extends DadsSettings(config)
{

  override lazy val measurementReceiver: DadsSettings.ReceiverSettings =
    new DadsSettings.ReceiverSettings(config) {
      override lazy val host = "127.0.0.1"
      override lazy val port = 8080
    }
}
