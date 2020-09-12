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

class BucketRepositoryTest
  extends AsyncFlatSpec
    with Matchers
    with TimeLimits
    with BeforeAndAfter
    with Eventually {

  import BucketRepository._

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

  behavior of "BucketRepository"

  val repository: BucketRepository =
    BucketRepository(settings)(system.toTyped)

  after {
    system.terminate()
  }

  it should "round-trip addTo/getFrom measurements for all buckets" in {

    def assertRoundTrip[A](transformation: Measurement => A)(output: Seq[A]): Assertion =
      assert(output.toSet === fixture.map(transformation).toSet)

    def tripRoundWith[A](bucketOn: BucketOn): Instant => Future[Seq[Long]] =
      instant =>
        withFixture { m =>
          for {
            _ <- repository.addTo(bucketOn)(m)
            r <- repository.getFrom(bucketOn)(m.sourceId)(instant)
          } yield r
      }

    eventually {
      Future.sequence(
        Seq( tripRoundWith(BucketOn.Day)
           , tripRoundWith(BucketOn.Month)
           , tripRoundWith(BucketOn.MonthYear)
           , tripRoundWith(BucketOn.WeekYear)
           , tripRoundWith(BucketOn.Always)
        ).map(tripRoundWithAll => tripRoundWithAll(now))
      ).map(results => assertRoundTrip(_.value)(results.flatten))
    }
  }

  // utiLSS

  /* failed tests raise exceptions */
  val toSucceeded: Seq[Assertion] => Assertion =
    _ => succeed
}
