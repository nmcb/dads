/*
 * This is free and unencumbered software released into the public domain.
 *
 */

package dads.v1
package test
package data

import java.time._
import java.time.temporal._

import scala.util.Random

object RealWorld {

  import ChronoUnit._

  def now: Instant =
    Instant.now

  def future: Instant =
    now.plus(Main.RealTimeServiceLevelAgreement)

  def InstantUncertaintyMillis: TemporalAmount =
    Duration.ofMillis(10)

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
}
