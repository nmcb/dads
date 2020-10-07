/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package test
package data

import java.time._
import java.time.temporal._

import scala.jdk._
import scala.util._
import scala.concurrent.duration._

import org.scalacheck._

import org.scalatest.concurrent._
import org.scalatest.time._

trait RealTime
  extends PatienceConfiguration
{
  import ChronoUnit._

  import Span._
  import Gen._

  import DurationConverters._

  import DadsSettings._

  override final implicit val patienceConfig: PatienceConfig =
    PatienceConfig(RealTimeServiceLevelAgreement.toSpan
      , RealTimeServiceLevelAgreement.div(10).toSpan
    )

  final val InstantUncertaintyMillis: FiniteDuration =
    10.millis

  final lazy val realNow: Instant =
    Instant.now

  final lazy val futureNow: Instant =
    realNow.plus(RealTimeServiceLevelAgreement.toJava)

  final lazy val pastNow: Instant =
    realNow.minus(RealTimeServiceLevelAgreement.toJava)

  final lazy val instantUncertaintyAdjusterMillis: TemporalAdjuster =
    temporal => temporal.plus(Random.nextLong(10), MILLIS)

  final implicit lazy val implicitArbitraryRealNowInstant: Arbitrary[Instant] =
    Arbitrary(realNow.withUncertainty)

  final lazy val explicitArbitraryFutureNowInstant: Arbitrary[Instant] =
    Arbitrary(futureNow.withUncertainty)

  final lazy val explicitArbitraryPastNowInstant: Arbitrary[Instant] =
    Arbitrary(pastNow.withUncertainty)

  final lazy implicit val instantUncertainty: Uncertainty[Instant] =
    (instant: Instant) => instant.`with`(instantUncertaintyAdjusterMillis)

  // UTILS

  trait Uncertainty[A] {
    def spread(a: A): A
  }

  implicit class UncertaintyOps[A: Uncertainty](a: A) {

    val withUncertainty: A =
      implicitly[Uncertainty[A]].spread(a)
  }

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
