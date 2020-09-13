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
    with BeforeAndAfterAll
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

  def withMeasurements[A](f: Measurement => Future[A]): Future[Seq[A]] =
    Future.sequence(fixture.map(f))

  val repository: CounterRepository =
    CounterRepository(settings)(system.toTyped)

  behavior of "CounterRepository"

  override def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }


  it should "round-trip getFrom/addTo/getFrom measurements for all counters" in {

    def tripRoundWith[A](counterOn: CounterOn): Instant => Future[Seq[Assertion]] = { instant =>
      withMeasurements { measurement =>
        for {
          before  <- repository.getFrom(counterOn)(measurement.sourceId)(instant)
          _       <- repository.addTo(counterOn)(measurement)
          after   <- repository.getFrom(counterOn)(measurement.sourceId)(instant)
        } yield assert(after === before + measurement.adjustment)
      }
    }

    eventually {
      Future.sequence(
        Seq( tripRoundWith(CounterOn.Days)
           , tripRoundWith(CounterOn.Months)
           , tripRoundWith(CounterOn.MonthYears)
           , tripRoundWith(CounterOn.WeekYears)
           , tripRoundWith(CounterOn.SinceEpoch)
        ).map(tripRoundWithAll => tripRoundWithAll(now))
      ).map(toSucceeded)
    }
  }

  it should "round-trip getFrom/addToAll/getFrom measurements for all counters" in {

    case class Key(counterId: CounterId, measurement: Measurement)

    def loadAll(instant: Instant, measurements: Seq[Measurement]): Future[Map[Key,Long]] =
      Future.sequence(
        CounterOn.All
          .flatMap(counterOn => measurements.map(measurement => counterOn -> measurement)).toMap
          .map({ case (counterOn,measurement) =>
            repository
              .getFrom(counterOn)(measurement.sourceId)(measurement.instant)
              .map(counter => Key(counterOn(measurement.instant),measurement) -> counter)
          }).toSeq
      ).map(_.toMap)

    eventually {
      for {
        before <- loadAll(now, fixture)
        added  <- Future.sequence(fixture.map(m => repository.addToAll(m)))
        after  <- loadAll(now, fixture)
      } yield {
        assert(added.size === CounterOn.All.size)
        assert(before.keys.map(key => after(key) === before(key) + key.measurement.adjustment).forall(isTrue))
      }
    }
  }

  // UTILS

  /* failed tests raise exceptions */
  def toSucceeded: Seq[Seq[Assertion]] => Assertion =
    _ => succeed

  def isTrue: Boolean => Boolean =
    _ === true
}
