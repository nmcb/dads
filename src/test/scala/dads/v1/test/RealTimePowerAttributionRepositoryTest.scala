/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package test

import scala.concurrent._
import akka._
import akka.actor._
import akka.actor.typed.scaladsl.adapter._
import org.scalacheck._
import org.scalatest._
import org.scalatest.concurrent._
import org.scalatest.flatspec._
import org.scalatest.matchers.should._
import data._
import squants.energy.Power

class RealTimePowerAttributionRepositoryTest
  extends AsyncFlatSpec
    with Matchers
    with TimeLimits
    with BeforeAndAfterAll
    with Eventually
    with ArbitraryPower
{
  import Arbitrary._
  import RealTimePowerRepository._
  import DadsSettings._

  implicit val system: ActorSystem =
    ActorSystem("RealTimeDecimalRepositoryTestSystem")

  val settings: RepositorySettings =
    new DadsSettings().repositorySettings

  val fixture: Seq[PowerAttribution] = {
    // FIXME the worm at the core
    Seq.fill(1)(arbitrary[PowerAttribution].sample.get).sorted
  }

  val realTimeDecimalRepository: RealTimePowerRepository =
    RealTimePowerRepository.cassandra(settings)(system.toTyped)

  override def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }

  behavior of "RealTimeDecimalRepository"

  it should "update all decimals in set/getLast round-trip" in {

    val expectedResults: Map[SourceId,Power] =
      fixture.groupBy(_.sourceId).map {
        case (sourceId, readings) => sourceId -> readings.map(_.value).max
      }

    val inbound: Future[Seq[Done]] =
      Future.sequence(fixture.map(d => realTimeDecimalRepository.set(d)))

    eventually {
      inbound.flatMap(_ =>
        Future.sequence(
          expectedResults.map {
            case (sourceId, expectedValue) =>
              for {
                Some(decimal) <- realTimeDecimalRepository.getLast(sourceId)
              } yield assert(decimal.value === expectedValue, s"sourceId:$sourceId")
          }.toSeq
        )
      )
    }.map(toSucceed)
  }

  // UTILS

  /* failed tests raise exceptions */
  def toSucceed: Seq[Assertion] => Assertion =
    _ => succeed
}
