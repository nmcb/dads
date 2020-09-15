/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package tests

import java.time._

import dads.v1.CounterRepository.CounterOn
import org.scalatest.matchers.should._
import org.scalatest.prop._
import org.scalatest.propspec._

class CounterIdTest
  extends AnyPropSpec
    with TableDrivenPropertyChecks
    with Matchers {

  val now: Instant =
    Instant.now

  property("counterId.prevMinor should respect minorTimeUnit relative to the epoch") {
    new EmptyCountersOn {
      forAll(countersOn(now)) { counterId =>
        val prev = counterId.prevMinor.minorInstant.toEpochMilli
        val curr = counterId.minorInstant.toEpochMilli
        prev should be (curr - counterId.minorTimeUnit.getDuration.toMillis)
      }
    }
  }
}

trait CountersOn extends Tables {

  def countersOn(instant: Instant) =
    Table("counterOn", counterOnDaysByMonths(instant))

  def counterOnDaysByMonths: CounterOn
}

class EmptyCountersOn extends CountersOn {
  def counterOnDaysByMonths: CounterOn = CounterOn.DaysByMonths

}
