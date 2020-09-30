/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1

import org.scalacheck.Arbitrary
import org.scalacheck.Gen.choose
import org.scalatest.concurrent._

trait RealTime extends PatienceConfiguration {

  import java.time._
  import java.time.temporal._
  import ChronoUnit._

  import scala.concurrent.duration._
  import scala.jdk._
  import DurationConverters._

  import scala.util._

  import org.scalatest.time._
  import Span._

  import DadsSettings._

  val InstantSpread = 5 * YEARS.getDuration.dividedBy(2).toMillis

  def realNow(): Instant =
    Instant.now

  def futureNow(): Instant =
    realNow().plus(RealTimeServiceLevelAgreement.toJava).`with`(instantUncertaintyAdjusterMillis)

  final val InstantUncertaintyMillis: FiniteDuration =
    10.millis

  final val instantUncertaintyAdjusterMillis: TemporalAdjuster =
    temporal => temporal.plus(Random.nextLong(10) / 2, MILLIS)

  override final implicit val patienceConfig: PatienceConfig =
    PatienceConfig(RealTimeServiceLevelAgreement.toSpan, RealTimeServiceLevelAgreement.div(10).toSpan)

  trait Uncertainty[A] {
    def spread(a: A): A
  }

  implicit class RealTimeOps[A : Uncertainty](a: A) {

    val withUncertainty: A =
      implicitly[Uncertainty[A]].spread(a)
  }

  implicit def implicitInstantUncertainty: Uncertainty[Instant] =
    instant => instant.`with`(instantUncertaintyAdjusterMillis)

  implicit def implicitRealNowWithSpread: Arbitrary[Instant] =
    Arbitrary {
      val start = realNow().toEpochMilli - InstantSpread
      val end   = realNow().toEpochMilli + InstantSpread
      choose(start, end).map(Instant.ofEpochMilli)
    }

  // UTILS

  implicit class ScalaTestSpanConverter(span: Span) {

    def toJavaDuration: java.time.Duration =
      convertSpanToDuration(span).toJava

    def toScalaDuration: FiniteDuration =
      convertSpanToDuration(span)
  }

  implicit class ScalaFiniteDurationConverter(duration: FiniteDuration) {

    def toJavaDuration: java.time.Duration =
      convertSpanToDuration(duration).toJava

    def toSpan: org.scalatest.time.Span =
      convertDurationToSpan(duration)
  }
}
