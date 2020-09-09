/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package tests

import java.io._
import java.time.{Instant, LocalDate}
import java.util.UUID
import java.util.concurrent.Executor

import scala.concurrent._
import akka._
import akka.actor._
import akka.event._
import akka.actor.typed.scaladsl.adapter._
import org.scalatest.concurrent._
import org.scalatest._
import org.scalatest.matchers.should._
import org.scalatest.time._
import akka.persistence.cassandra.testkit._

class RepositoryTest
  extends flatspec.AsyncFlatSpec
    with Matchers
    with TimeLimits
    with BeforeAndAfterAll
    with Eventually {

  val databaseDirectory: File =
    new File(s"target/cassandra-${UUID.randomUUID}")


  implicit val system: ActorSystem =
    ActorSystem("repository-test-system")

  implicit val executor: Executor =
    system.dispatcher

  implicit val log: LoggingAdapter =
    system.log

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(Span(5, Seconds), Span(250, Millis))

//  override protected def beforeAll(): Unit = {
//    CassandraLauncher.start(
//      databaseDirectory,
//      CassandraLauncher.DefaultTestConfigResource,
//      clean = true,
//      port = 19042, // default is 9042, but use different for test
//      CassandraLauncher.classpathForResources("logback-test.xml"))
//
//    Repository.createTables(system.toTyped)
//
//    super.beforeAll()
//  }
//
//  override protected def afterAll(): Unit = {
//    super.afterAll()
//    CassandraLauncher.stop()
//  }

  val fixture: List[Measurement] =
    List( Measurement(UUID.randomUUID, Instant.now, Instant.now, 666L)
        , Measurement(UUID.randomUUID, Instant.now, Instant.now, 667L)
        , Measurement(UUID.randomUUID, Instant.now, Instant.now, 668L)
        , Measurement(UUID.randomUUID, Instant.now, Instant.now, 669L)
        , Measurement(UUID.randomUUID, Instant.now, Instant.now, 670L)
        )

  behavior of "Repository"

  it should "round-trip write-read day measurements" in {
    val repository = Repository(system.toTyped)
    val insert = Future.sequence(fixture.map(m => repository.insertDay(m)))
    insert.map(l => assert(l.size == fixture.size))
  }
}
