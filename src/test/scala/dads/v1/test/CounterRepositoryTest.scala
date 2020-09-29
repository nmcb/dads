/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package test

import java.time._

import scala.concurrent._

import akka.actor._
import akka.event._
import akka.actor.typed.scaladsl.adapter._

import org.scalatest._
import org.scalatest.concurrent._
import org.scalatest.flatspec._
import org.scalatest.matchers.should._

import org.scalacheck._

import data._

class CounterRepositoryTest
  extends AsyncFlatSpec
    with CounterRepositoryData
    with Matchers
    with TimeLimits
    with BeforeAndAfterAll
    with Eventually
    with RealWorld {

  import Arbitrary._
  import CounterRepository._
  import ArbitraryCounters._

  final val FixtureSize = 100

  implicit val system: ActorSystem =
    ActorSystem("CounterRepositoryTestSystem")

  implicit val log: LoggingAdapter =
    system.log

  val settings: DadsSettings =
    DadsSettings()

  val fixture: Seq[Adjustment] =
    Seq.fill(FixtureSize)(arbitrary[Adjustment].sample.get)

  def withAdjustments[A](adjustments: Seq[Adjustment])(f: Adjustment => Future[A]): Future[Seq[A]] =
    Future.sequence(adjustments.map(f))

  val counterRepository: CounterRepository =
    CounterRepository(settings)(system.toTyped)

  override def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }

  behavior of "CounterRepository"

  it should "update all counters in a baselined addTo/getFrom/addTo/getFrom round-trip" in {

    def tripRoundWith[A](counterOn: CounterOn): Seq[Adjustment] => Instant => Future[Seq[Assertion]] = {
      // TODO forAll adjustment and instant ?
      adjustments => instant =>
        withAdjustments(adjustments) { adjustment =>
          for {
            before  <- counterRepository.getFrom(counterOn)(adjustment.sourceId)(instant)
            _       <- counterRepository.addTo(counterOn)(adjustment)
            after   <- counterRepository.getFrom(counterOn)(adjustment.sourceId)(instant)
          } yield assert(after === before + adjustment.value)
        }
    }

    eventually {
      Future.sequence(
        Seq( tripRoundWith(CounterOn.HourByDayCounterOn)
           , tripRoundWith(CounterOn.DayByMonthCounterOn)
           , tripRoundWith(CounterOn.MonthByYearCounterOn)
           , tripRoundWith(CounterOn.WeekByYearCounterOn)
           , tripRoundWith(CounterOn.YearCounterOn))
          .map(tripRoundWith => tripRoundWith(fixture))
          .map(tripRoundWithAll => tripRoundWithAll(futureNow))
      ).map(toSucceeded)
    }
  }

  // UTILS

  /* failed tests raise exceptions */
  def toSucceeded: Seq[Seq[Assertion]] => Assertion =
    _ => succeed

  def isTrue: Boolean => Boolean =
    _ === true
}
