/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1

import com.typesafe.config._

class DadsSettings private (config: Config) {

  lazy val realtimeKeyspace: String =
    config.getConfig("cassandra").getString("realtime-keyspace")

  lazy val counterKeyspace: String =
    config.getConfig("cassandra").getString("counter-keyspace")

}

object DadsSettings {

  def apply(): DadsSettings =
    new DadsSettings(ConfigFactory.defaultApplication().getConfig("dads"))
}