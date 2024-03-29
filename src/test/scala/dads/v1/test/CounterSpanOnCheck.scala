/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package test

import java.time._

import org.scalacheck._
import org.scalactic._

import data._

object CounterSpanOnCheck
  extends Properties("CounterSpanOn")
    with ArbitraryCounters
{
  import Prop._
  import TypeCheckedTripleEquals._
  import CounterRepository._

  property("apply(instant) should not result in duplicate counters") =
    forAll { (instant: Instant, counterSpanOn: CounterSpanOn) =>
      val counterSpan = counterSpanOn(instant)
      counterSpan.length === counterSpan.toSet.size
    }

  property("apply(instant) should return counters sorted descending by minor instant") =
    forAll { (instant: Instant, counterSpanOn: CounterSpanOn) =>
      val counterSpan = counterSpanOn(instant)
      counterSpan === counterSpan.sorted(Counter.descendingCounterOrder)
    }
}