/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1

import java.time._
import java.time.temporal._

import scala.concurrent.duration._

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

  final val RealTimeServiceLevelAgreement: FiniteDuration =
    3.seconds

  final val RealTimeChronoUnit: ChronoUnit =
    ChronoUnit.MILLIS

  final val RealTimeToLive: FiniteDuration =
    1.day

  final val TimeZoneOfRepositoryOffset: ZoneOffset =
    ZoneOffset.UTC

  final val FirstDayOfRepositoryWeek  : DayOfWeek  =
    DayOfWeek.MONDAY

  // Inbound limits MeasurementService

  final val MaxSourceIdsPerIndication  = 5
  final val MaxMeasurementsPerSourceId = 5

  final val MinDecimalReadingValue = 1L
  final val MaxDecimalReadingValue = 1000L

  final val MinAdjustmentValue = 1L     // FIXME add adjustment input validation subject to unit conversion
  final val MaxAdjustmentValue = 1000L

  final val MaxCounterSpanSize = 500



  def apply(): DadsSettings =
    new DadsSettings(ConfigFactory.defaultApplication.getConfig("dads"))

  def apply(config: Config): DadsSettings =
    new DadsSettings(config)

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
