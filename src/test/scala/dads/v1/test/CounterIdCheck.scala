/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package test

import java.time._
import java.time.temporal.ChronoUnit

import org.scalacheck._

object CounterIdCheck
  extends Properties("CounterId")
    with data.CounterRepositoryData {

  import Prop._
  import CounterIdData._
  import CounterRepository._

  def truncation(chronoUnit: ChronoUnit): String =
    chronoUnit match {
      case ChronoUnit.HOURS   =>               "00:00Z"
      case ChronoUnit.DAYS    =>           "T00:00:00Z"
      case ChronoUnit.WEEKS   =>           "T00:00:00Z" // FIXME could compute a longer postfix
      case ChronoUnit.MONTHS  =>         "01T00:00:00Z"
      case ChronoUnit.YEARS   =>      "01-01T00:00:00Z"
      case ChronoUnit.FOREVER => "1970-01-01T00:00:00Z"
      case _                  => throw new IllegalArgumentException(s"chronoUnit = $chronoUnit")
    }

  property("CounterId should respect truncation in representation") =
    forAll { (instant: Instant, counterOn: CounterOn) =>
      val counterId = counterOn(instant)
      (   counterId.minorInstant.toString.endsWith(truncation(counterId.minorChronoUnit))
      &&  counterId.majorInstant.toString.endsWith(truncation(counterId.majorChronoUnit)))
    }
}