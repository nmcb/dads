/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package tests

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
import org.scalatest.time._

class CounterRepositoryTest
  extends AsyncFlatSpec
    with Matchers
    with TimeLimits
    with BeforeAndAfter
    with Eventually {

  import CounterRepository._

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(Span(3, Seconds), Span(250, Millis))

  implicit val system: ActorSystem =
    ActorSystem("RepositoryTestSystem")

  implicit val log: LoggingAdapter =
    system.log

  val settings: DadsSettings =
    DadsSettings()

  val now: Instant =
    Instant.now

  val fixture: Seq[Measurement] =
    List( Measurement(UUID.randomUUID, now, 666L)
        , Measurement(UUID.randomUUID, now, 667L)
        , Measurement(UUID.randomUUID, now, 668L)
        , Measurement(UUID.randomUUID, now, 669L)
        , Measurement(UUID.randomUUID, now, 670L)
        )

  def withFixture[A](f: Measurement => Future[A]): Future[Seq[A]] =
    Future.sequence(fixture.map(f))

  behavior of "CounterRepository"

  val repository: CounterRepository =
    CounterRepository(settings)(system.toTyped)

  after {
    system.terminate()
  }

  it should "round-trip getFrom/addTo/getFrom measurements for all counters" in {

    def tripRoundWith[A](counterOn: CounterOn): Instant => Future[Seq[Assertion]] = { instant =>
      withFixture { measurement =>
        for {
          before  <- repository.getFrom(counterOn)(measurement.sourceId)(instant)
          _       <- repository.addTo(counterOn)(measurement)
          after   <- repository.getFrom(counterOn)(measurement.sourceId)(instant)
        } yield assert(after === before + measurement.adjustment)
      }
    }

    eventually {
      Future.sequence(
        Seq( tripRoundWith(CounterOn.Day)
           , tripRoundWith(CounterOn.Month)
           , tripRoundWith(CounterOn.MonthYear)
           , tripRoundWith(CounterOn.WeekYear)
           , tripRoundWith(CounterOn.Always)
        ).map(tripRoundWithAll => tripRoundWithAll(now))
      ).map(toSucceeded)
    }
  }

  // UTILS

  /* failed tests raise exceptions */
  val toSucceeded: Seq[Seq[Assertion]] => Assertion =
    _ => succeed
}
