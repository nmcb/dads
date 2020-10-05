/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1.test.data

import java.time._

import dads.v1.DadsSettings._
import dads.v1.{CounterRepository, SourceId}
import org.scalacheck._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen.{choose, oneOf}

trait ArbitraryCounters
  extends RealTime
  with ArbitrarySources
{
  import CounterRepository._
  import CounterOn._
  import CounterSpanOn._

  implicit val arbitraryCounterOn: Arbitrary[CounterOn] =
    Arbitrary(
      oneOf(HourByDayCounterOn
        , DayByMonthCounterOn
        , MonthByYearCounterOn
        , WeekByYearCounterOn
        , YearCounterOn
      ))

  implicit val arbitraryCounterInstant: Arbitrary[Counter] =
    Arbitrary {
      for {
        instant <- arbitrary[Instant]
        counterOn <- arbitrary[CounterOn]
      } yield counterOn(instant)
    }

  implicit val arbitraryCounterSpanOn: Arbitrary[CounterSpanOn] =
    Arbitrary {
      for {
        spanOf <- oneOf(HourByDaySpanOf
          , DayByMonthSpanOf
          , MonthByYearSpanOf
          , WeekByYearSpanOf
          , YearSpanOf
        )
        size <- choose(1, MaxCounterSpanSize)
      } yield spanOf(size)
    }

  implicit val arbitraryAdjustment: Arbitrary[Adjustment] =
    Arbitrary {
      for {
        sourceId <- arbitrary[SourceId]
        value <- choose(MinAdjustmentValue, MaxAdjustmentValue)
      } yield Adjustment(sourceId, realNow, value)
    }
}
