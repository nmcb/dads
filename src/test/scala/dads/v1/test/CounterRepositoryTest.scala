/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package test

import java.time._
import java.util.UUID

import scala.concurrent._

import akka.actor._
import akka.event._
import akka.actor.typed.scaladsl.adapter._

import org.scalatest._
import org.scalatest.concurrent._
import org.scalatest.flatspec._
import org.scalatest.matchers.should._

import data._

class CounterRepositoryTest
  extends AsyncFlatSpec
    with CounterRepositoryData
    with Matchers
    with TimeLimits
    with BeforeAndAfterAll
    with Eventually
    with RealWorld {

  import CounterRepository._

  implicit val system: ActorSystem =
    ActorSystem("CounterRepositoryTestSystem")

  implicit val log: LoggingAdapter =
    system.log

  val settings: DadsSettings =
    DadsSettings()

  // FIXME Use arbitrary adjustments
  val fixture: Seq[Adjustment] = {
    List( Adjustment(UUID.randomUUID, now.spread, 666L)
        , Adjustment(UUID.randomUUID, now.spread, 667L)
        , Adjustment(UUID.randomUUID, now.spread, 668L)
        , Adjustment(UUID.randomUUID, now.spread, 669L)
        , Adjustment(UUID.randomUUID, now.spread, 670L)
        )
  }

  def withAdjustments[A](adjustments: Seq[Adjustment])(f: Adjustment => Future[A]): Future[Seq[A]] =
    Future.sequence(adjustments.map(f))

  val repository: CounterRepository =
    CounterRepository(settings)(system.toTyped)

  override def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }

  behavior of "CounterRepository"

  it should "round-trip getFrom/addTo/getFrom should be able to update all counters" in {

    def tripRoundWith[A](counterOn: CounterOn): Seq[Adjustment] => Instant => Future[Seq[Assertion]] =
      adjustments => instant =>
        withAdjustments(adjustments) { adjustment =>
          for {
            before  <- repository.getFrom(counterOn)(adjustment.sourceId)(instant)
            _       <- repository.addTo(counterOn)(adjustment)
            after   <- repository.getFrom(counterOn)(adjustment.sourceId)(instant)
          } yield assert(after === before + adjustment.value)
        }

    eventually {
      Future.sequence(
        Seq( tripRoundWith(CounterOn.HoursByDay)
           , tripRoundWith(CounterOn.DaysByMonth)
           , tripRoundWith(CounterOn.MonthsByYear)
           , tripRoundWith(CounterOn.WeeksByYear)
           , tripRoundWith(CounterOn.Years))
          .map(tripRoundWith => tripRoundWith(fixture))
          .map(tripRoundWithAll => tripRoundWithAll(futureNow))
      ).map(toSucceeded)
    }
  }

  it should "round-trip getFrom/addToAll/getFrom adjustments for all counters" in {

    case class Key(counterInstant: CounterInstant, adjustment: Adjustment)

    def loadAll(adjustments: Seq[Adjustment])(instant: Instant): Future[Map[Key,Long]] =
      Future.sequence(
        CounterOn.All
          .flatMap(counterOn => adjustments.map(adjustment => counterOn -> adjustment)).toMap
          .map({ case (counterOn,adjustment) =>
            repository
              .getFrom(counterOn)(adjustment.sourceId)(instant)
              .map(counter => Key(counterOn(adjustment.instant),adjustment) -> counter)
          }).toSeq
      ).map(_.toMap)

    eventually {
      for {
        before <- loadAll(fixture)(now)
        added  <- Future.sequence(fixture.map(adjustment => repository.addToAll(adjustment)))
        after  <- loadAll(fixture)(futureNow)
      } yield {
        assert(added.size === CounterOn.All.size)
        assert(before.keys.map(key => after(key) === before(key) + key.adjustment.value).forall(isTrue))
      }
    }
  }

  // FIXME add getSpan tests

  // UTILS

  /* failed tests raise exceptions */
  def toSucceeded: Seq[Seq[Assertion]] => Assertion =
    _ => succeed

  def isTrue: Boolean => Boolean =
    _ === true
}
