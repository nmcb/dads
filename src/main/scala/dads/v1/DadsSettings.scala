/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1

import com.typesafe.config._

class DadsSettings private (config: Config) {

  import DadsSettings._

  lazy val realtimeKeyspace: String =
    config.getConfig("cassandra").getString("realtime-keyspace")

  lazy val counterKeyspace: String =
    config.getConfig("cassandra").getString("counter-keyspace")

  lazy val measurementReceiver: ReceiverSettings =
    ReceiverSettings(config.getConfig("receivers.measurement"))

}

object DadsSettings {

  def apply(): DadsSettings =
    new DadsSettings(ConfigFactory.defaultApplication().getConfig("dads"))

  class ReceiverSettings private(config: Config) {

    lazy val host: String =
      config.getString("host")

    lazy val port: Int =
      config.getInt("port")
  }

  object ReceiverSettings {

    def apply(config: Config): ReceiverSettings =
      new ReceiverSettings(config)
  }
}
