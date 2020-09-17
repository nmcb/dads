/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package test

import java.time.temporal._

import org.scalacheck._

object CounterIdCheck
  extends Properties("CounterId")
    with data.CounterRepositoryData {

  import Prop._
  import ArbitraryCounters._
  import CounterRepository._

  def truncation(chronoUnit: ChronoUnit): String =
    chronoUnit match {
      case ChronoUnit.HOURS   =>               "00:00Z"
      case ChronoUnit.DAYS    =>           "T00:00:00Z"
      case ChronoUnit.WEEKS   =>           "T00:00:00Z" // FIXME could compute a less resolute postfix
      case ChronoUnit.MONTHS  =>         "01T00:00:00Z"
      case ChronoUnit.YEARS   =>      "01-01T00:00:00Z"
      case ChronoUnit.FOREVER => "1970-01-01T00:00:00Z"
      case _                  => throw new IllegalArgumentException(s"chronoUnit=$chronoUnit")
    }


  property("majorInstant truncates string representation") =
    forAll { (counterId: CounterId) =>
      counterId.majorInstant.toString endsWith truncation(counterId.majorChronoUnit)
    }

  property("minorInstant truncates string representation") =
    forAll { (counterId: CounterId) =>
      counterId.minorInstant.toString endsWith truncation(counterId.minorChronoUnit)
    }

  property("sampleBefore <= minorInstant relative to the epoch") =
    forAll { (counterId: CounterId) =>
      counterId.sampleBefore.toEpochMilli <= counterId.minorInstant.toEpochMilli
    }
}