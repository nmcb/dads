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

import org.scalatest.concurrent._
import org.scalatest._
import org.scalatest.matchers.should._
import org.scalatest.time._


class BucketRepositoryTest
  extends flatspec.AsyncFlatSpec
    with Matchers
    with TimeLimits
    with BeforeAndAfterAll
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

  override protected def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }

  val now: Instant =
    Instant.now

  val fixture: List[Measurement] =
    List( Measurement(UUID.randomUUID, now, 666L)
        , Measurement(UUID.randomUUID, now, 667L)
        , Measurement(UUID.randomUUID, now, 668L)
        , Measurement(UUID.randomUUID, now, 669L)
        , Measurement(UUID.randomUUID, now, 670L)
        )

  behavior of "Repository"

  it should "round-trip writing/reading measurements in the day bucket" in {
    val repository = BucketRepository(settings)(system.toTyped)

    eventually( for {
      added <- Future.sequence(fixture.map(m => repository.addTo(BucketFor.Day(m.instant))(m)))
      found <- Future.sequence(fixture.map(m => repository.getFrom(BucketFor.Day(now))(m.sourceId)))
      if (added.size == fixture.size && found.toSet == fixture.map(_.value).toSet)
    } yield succeed)
  }
}
