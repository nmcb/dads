/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package test

import java.time._

import akka.actor._
import akka.actor.typed.scaladsl.adapter._
import akka.event._

import org.scalacheck._
import org.scalatest._
import org.scalatest.concurrent._
import org.scalatest.flatspec._
import org.scalatest.matchers.should._

import scala.concurrent._

import data._

class RealTimeRepositoryTest
  extends AsyncFlatSpec
    with RepositoryData
    with Matchers
    with TimeLimits
    with BeforeAndAfterAll
    with Eventually
    with RealWorld {

  import Arbitrary._
  import ArbitraryDecimals._
  import RealTimeDecimalRepository._

  final val FixtureSize = 100

  implicit val system: ActorSystem =
    ActorSystem("RealTimeRepositoryTestSystem")

  implicit val log: LoggingAdapter =
    system.log

  val settings: DadsSettings =
    DadsSettings()

  val fixture: Seq[Decimal] =
    Seq.fill(FixtureSize)(arbitrary[Decimal].sample.get).sorted

  def withDecimals[A](adjustments: Seq[Decimal])(f: Decimal => Future[A]): Future[Seq[A]] =
    Future.sequence(adjustments.map(f))

  val realTimeDecimalRepository: RealTimeDecimalRepository =
    RealTimeDecimalRepository.cassandra(settings)(system.toTyped)

  override def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }

  behavior of "RealTimeDecimalRepository"

  it should "update all decimals in a baselined addTo/getFrom/addTo/getFrom round-trip" in {

    def tripRoundWith: Seq[Decimal] => Future[Seq[Assertion]] = {
      // TODO forAll adjustment and instant ?
      decimals =>
        withDecimals(decimals) { decimal =>
          for {
            before <- realTimeDecimalRepository.getLast(decimal.sourceId)
            _      <- realTimeDecimalRepository.set(decimal)
            after  <- realTimeDecimalRepository.getLast(decimal.sourceId)
          } yield assert(before === None && after.headOption === Some(decimal))
        }
    }

    eventually {
      tripRoundWith(fixture).map(toSucceeded)
    }
  }

  // UTILS

  /* failed tests raise exceptions */
  def toSucceeded: Seq[Assertion] => Assertion =
    _ => succeed
}
