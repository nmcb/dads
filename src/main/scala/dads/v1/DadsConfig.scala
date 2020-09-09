/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1

import com.typesafe.config._

class DadsSettings private (config: Config) {

  val realtimeKeyspace: String =
    config.getConfig("cassandra").getString("realtime-keyspace")

  val cumulativeKeyspace: String =
    config.getConfig("cassandra").getString("cumulative-keyspace")
}

object DadsSettings {

  def apply(): DadsSettings =
    new DadsSettings(ConfigFactory.defaultApplication().getConfig("dads"))
}
