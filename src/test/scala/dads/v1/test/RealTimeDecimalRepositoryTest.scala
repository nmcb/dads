/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package test

import akka.Done
import akka.actor._
import akka.actor.typed.scaladsl.adapter._
import org.scalacheck._
import org.scalatest._
import org.scalatest.concurrent._
import org.scalatest.flatspec._
import org.scalatest.matchers.should._

import scala.concurrent._
import data._

class RealTimeDecimalRepositoryTest
  extends AsyncFlatSpec
    with Matchers
    with TimeLimits
    with BeforeAndAfterAll
    with Eventually
    with ArbitraryDecimals
{
  import Arbitrary._
  import RealTimeDecimalRepository._
  import DadsSettings.RepositorySettings

  implicit val system: ActorSystem =
    ActorSystem("RealTimeDecimalRepositoryTestSystem")

  val settings: RepositorySettings =
    new DadsSettings().repositorySettings

  val fixture: Seq[Decimal] =
    Seq.fill(10)(arbitrary[Decimal].sample.get).sorted

  val realTimeDecimalRepository: RealTimeDecimalRepository =
    RealTimeDecimalRepository.cassandra(settings)(system.toTyped)

  override def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }

  behavior of "RealTimeDecimalRepository"

  it should "update all decimals in set/getLast round-trip" in {

    val expectedResults: Map[SourceId,BigDecimal] =
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
