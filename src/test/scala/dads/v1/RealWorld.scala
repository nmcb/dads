/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1

import org.scalatest.concurrent._

trait RealWorld extends PatienceConfiguration {

  import java.time._
  import java.time.temporal._
  import ChronoUnit._

  import Main._
  import org.scalatest.time._
  import Span._

  import scala.concurrent.duration._
  import scala.jdk._
  import DurationConverters._
  import scala.util._

  def now: Instant =
    Instant.now

  def futureNow: Instant =
    now.plus(RealTimeServiceLevelAgreement.toJava).`with`(instantUncertaintyAdjusterMillis)

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(RealTimeServiceLevelAgreement.toSpan, RealTimeServiceLevelAgreement.div(10).toSpan)

  def InstantUncertaintyMillis: FiniteDuration =
    10.millis

  def instantUncertaintyAdjusterMillis: TemporalAdjuster =
    temporal => temporal.plus(Random.nextLong(10), MILLIS)

  trait Uncertainty[A] {
    def spread(a: A): A
  }

  implicit class RealWorldOps[A : Uncertainty]
    (a: A) {
      def spread: A = implicitly[Uncertainty[A]].spread(a)
    }

  implicit val instantUncertainty: Uncertainty[Instant] =
    instant => instant.`with`(instantUncertaintyAdjusterMillis)

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
