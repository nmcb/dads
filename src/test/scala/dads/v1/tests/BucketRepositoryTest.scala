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
import dads.v1.BucketRepository
import org.scalatest._
import org.scalatest.concurrent._
import org.scalatest.flatspec._
import org.scalatest.matchers.should._
import org.scalatest.time._

import scala.util.{Failure, Success}

class BucketRepositoryTest
  extends AsyncFlatSpec
    with Matchers
    with TimeLimits
    with BeforeAndAfter
    with Eventually {

  import BucketRepository._
  import BucketFor._

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

  it should "round-trip writing/reading measurements in all buckets" in {


    def assertRoundTrip[A](transformation: Measurement => A)(output: Seq[A]): Assertion =
      assert(output.toSet === fixture.map(transformation).toSet)

    def tripRoundWith(bucketOn: BucketOn): Instant => Future[Assertion] =
      instant => for {
        _     <- withFixture(m => repository.addTo(bucketOn(m.instant))(m))
        found <- withFixture(m => repository.getFrom(bucketOn(instant))(m.sourceId))
      } yield assertRoundTrip(_.value)(found)

    eventually {
      Future.sequence(Seq(
          tripRoundWith(BucketFor.Day)(now)
        , tripRoundWith(BucketFor.Month)(now)
        , tripRoundWith(BucketFor.Year)(now)
        , tripRoundWith(BucketFor.WeekYear)(now)
        , tripRoundWith(BucketFor.Forever)(now)
      )).map(toSucceeded)
    }
  }

  // Utils

  /* failed test are raised exceptions */
  val toSucceeded: Seq[Assertion] => Assertion =
    _ => succeed
}
