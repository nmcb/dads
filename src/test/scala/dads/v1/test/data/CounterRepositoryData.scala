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

    import Instant._
    import ChronoUnit._

    val YearSpread    = 5
    val MaxSpanLength = 5

    implicit val arbitraryYearInstant: Arbitrary[Instant] =
      Arbitrary {
        val start = EPOCH.toEpochMilli
        val end   = now.toEpochMilli + YearSpread * YEARS.getDuration.dividedBy(2).toMillis
        choose(start, end).map(Instant.ofEpochMilli)
      }

    implicit val arbitraryCounterOn: Arbitrary[CounterOn] =
      Arbitrary(oneOf(CounterOn.All))

    implicit val arbitraryCounterInstant: Arbitrary[CounterInstant] =
      Arbitrary {
        for {
          instant   <- arbitrary[Instant]
          counterOn <- arbitrary[CounterOn]
        } yield counterOn(instant)
      }

    implicit val arbitraryCounterSpanOn: Arbitrary[CounterSpanOn] =
      Arbitrary {
        for {
          length    <- choose(1, MaxSpanLength)
          counterOn <- arbitrary[CounterOn]
        } yield CounterSpanOn(counterOn)(length)
      }
  }
}
