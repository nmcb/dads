/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package test
package data

import java.time._

import org.scalacheck._

trait RepositoryData { this: RealTime =>

  import Arbitrary._
  import Gen._

  import DadsSettings._

  object ArbitraryCounters {

    import CounterRepository._
    import CounterOn._
    import CounterSpanOn._

    implicit val arbitraryCounterOn: Arbitrary[CounterOn] =
      Arbitrary(
        oneOf( HourByDayCounterOn
             , DayByMonthCounterOn
             , MonthByYearCounterOn
             , WeekByYearCounterOn
             , YearCounterOn
             ))

    implicit val arbitraryCounterInstant: Arbitrary[Counter] =
      Arbitrary {
        for {
          instant   <- arbitrary[Instant]
          counterOn <- arbitrary[CounterOn]
        } yield counterOn(instant)
      }

    implicit val arbitraryCounterSpanOn: Arbitrary[CounterSpanOn] =
      Arbitrary {
        for {
          spanOf <- oneOf( HourByDaySpanOf
                         , DayByMonthSpanOf
                         , MonthByYearSpanOf
                         , WeekByYearSpanOf
                         , YearSpanOf
                         )
          size   <- choose(1, MaxCounterSpanSize)
        } yield spanOf(size)
      }

    implicit val arbitraryAdjustment: Arbitrary[Adjustment] =
      Arbitrary {
        for {
          sourceId <- arbitrary[SourceId]
          value    <- choose(MinAdjustmentValue, MaxAdjustmentValue)
        } yield Adjustment(sourceId, realNow().withUncertainty, value)
      }
  }

  object ArbitraryDecimals {

    import RealTimeDecimalRepository._

    implicit val arbitraryDecimal: Arbitrary[Decimal] =
      Arbitrary {
        for {
          sourceId <- arbitrary[SourceId]
          instant  <- arbitrary[Instant]
          value    <- choose(MinDecimalReadingValue, MaxDecimalReadingValue)
        } yield Decimal(sourceId, instant, value)
      }
  }
}