/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package test
package data

import java.time._
import java.time.temporal.ChronoUnit

import org.scalacheck._

trait CounterRepositoryData {

  import Arbitrary._
  import Gen._
  import CounterRepository._

  object ArbitraryCounters {

    val YearSpread = 5

    implicit val arbitraryYearInstant: Arbitrary[Instant] =
      Arbitrary {
        val start = Instant.EPOCH.toEpochMilli
        val end   = Instant.now.toEpochMilli + YearSpread * ChronoUnit.YEARS.getDuration.dividedBy(2).toMillis
        choose(start, end).map(Instant.ofEpochMilli)
      }

    implicit val arbitraryCounterOn: Arbitrary[CounterOn] =
      Arbitrary(oneOf(CounterOn.All))

    implicit val arbitraryCounterId: Arbitrary[CounterId] =
      Arbitrary {
        for {
          instant   <- arbitrary[Instant]
          counterOn <- arbitrary[CounterOn]
        } yield counterOn(instant)
      }

    val MaxLength = 500

    implicit val arbitraryCounterSpanOn: Arbitrary[CounterSpanOn] =
      Arbitrary {
        for {
          length    <- choose(1, MaxLength)
          counterOn <- arbitrary[CounterOn]
        } yield CounterSpanOn(counterOn)(length)
      }
  }
}
