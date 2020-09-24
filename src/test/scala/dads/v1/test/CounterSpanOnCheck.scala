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
    with CounterRepositoryData
    with RealWorld {

  import CounterRepository._
  import ArbitraryCounters._
  import Prop._
  import TypeCheckedTripleEquals._

  property("apply(instant) should not result in duplicate counter ids") =
    forAll { (instant: Instant, counterSpanOn: CounterSpanOn) =>
      val counterSpan = counterSpanOn(instant)
      counterSpan.length === counterSpan.toSet.size
    }

  property("apply(instant) should return counter instants sorted descending by minor instant") =
    forAll { (instant: Instant, counterSpanOn: CounterSpanOn) =>
      val counterSpan = counterSpanOn(instant)
      counterSpan === counterSpan.sorted(Counter.counterInstantDescendingOrder)
    }
}