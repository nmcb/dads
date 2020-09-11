/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package tests

import java.io._
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
    PatienceConfig(Span(5, Seconds), Span(250, Millis))

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

  val fixture: List[Measurement] =
    List( Measurement(UUID.randomUUID, Instant.now, 666L)
        , Measurement(UUID.randomUUID, Instant.now, 667L)
        , Measurement(UUID.randomUUID, Instant.now, 668L)
        , Measurement(UUID.randomUUID, Instant.now, 669L)
        , Measurement(UUID.randomUUID, Instant.now, 670L)
        )

  behavior of "Repository"

  it should "round-trip write-read day measurements" in {
    val repository = BucketRepository(settings)(system.toTyped)
    val insert     = Future.sequence(fixture.map(m => repository.addToDayBucket(m)))

    eventually(insert.map(l => assert(l.forall(_ == true))))
  }
}
