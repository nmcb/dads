/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package test

import java.time.temporal._

import org.scalacheck._

import data._

object CounterCheck
  extends Properties("Counter")
  with ArbitraryCounters
{
  import Prop._
  import CounterRepository._

  def truncation(chronoUnit: ChronoUnit): String =
    chronoUnit match {
      case ChronoUnit.HOURS   =>               "00:00Z"
      case ChronoUnit.DAYS    =>           "T00:00:00Z"
      case ChronoUnit.WEEKS   =>           "T00:00:00Z"
      case ChronoUnit.MONTHS  =>         "01T00:00:00Z"
      case ChronoUnit.YEARS   =>      "01-01T00:00:00Z"
      case ChronoUnit.FOREVER => "1970-01-01T00:00:00Z"
      case _                  => throw new IllegalArgumentException(s"chronoUnit=$chronoUnit")
    }

  property("majorInstant truncates string representation") =
    forAll { (counter: Counter) =>
      counter.majorInstant.toString endsWith truncation(counter.majorChronoUnit)
    }

  property("minorInstant truncates string representation") =
    forAll { (counter: Counter) =>
      counter.minorInstant.toString endsWith truncation(counter.minorChronoUnit)
    }

  property("prevMinorInstant <= minorInstant relative to the epoch") =
    forAll { (counter: Counter) =>
      counter.prevMinorInstant.toEpochMilli <= counter.minorInstant.toEpochMilli
    }
}