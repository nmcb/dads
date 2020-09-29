/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package test
package data

import java.time._
import java.time.temporal._

import org.scalacheck._

trait RepositoryData { this: RealWorld =>

  import Arbitrary._
  import Gen._

  object ArbitraryCounters {

    import CounterRepository._
    import CounterOn._
    import CounterSpanOn._

    val MaxSpanLength      = 500
    val MinAdjustmentValue = 1L        // FIXME add adjustment input validation subject to unit conversion
    val MaxAdjustmentValue = 1000L

    implicit val arbitraryCounterOn: Arbitrary[CounterOn] =
      Arbitrary(oneOf(
        Seq( HourByDayCounterOn
           , DayByMonthCounterOn
           , MonthByYearCounterOn
           , WeekByYearCounterOn
           , YearCounterOn
           )))

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
          spanOf <- oneOf(HourByDaySpanOf, DayByMonthSpanOf, MonthByYearSpanOf, WeekByYearSpanOf, YearSpanOf)
          size   <- choose(1, MaxSpanLength)
        } yield spanOf(size)
      }

    implicit val arbitraryAdjustment: Arbitrary[Adjustment] =
      Arbitrary {
        for {
          sourceId <- arbitrary[SourceId]
          value    <- choose(MinAdjustmentValue, MaxAdjustmentValue)
        } yield Adjustment(sourceId, now.spread, value)
      }
  }

  object ArbitraryDecimals {

    val MinDecimalValue = 1L        // FIXME add adjustment input validation subject to unit conversion
    val MaxDecimalValue = Long.MaxValue

    import RealTimeDecimalRepository._

    implicit val arbitraryDecimal: Arbitrary[Decimal] =
      Arbitrary {
        for {
          sourceId <- arbitrary[SourceId]
          instant  <- arbitrary[Instant]
          value    <- choose(MinDecimalValue, MinDecimalValue)
        } yield Decimal(sourceId, instant, value)
      }
  }
}
