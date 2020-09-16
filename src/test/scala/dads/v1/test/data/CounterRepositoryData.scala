/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package test
package data

import java.time._
import org.scalacheck._

trait CounterRepositoryData {

  import Gen._
  import CounterRepository._

  object CounterIdData {

    implicit val arbitraryInstant: Arbitrary[Instant] =
      Arbitrary(
        choose(Instant.EPOCH.toEpochMilli, Instant.now.toEpochMilli)
          .map(Instant.ofEpochMilli(_)))

    implicit val arbitraryCounterOn: Arbitrary[CounterOn] =
      Arbitrary(
        oneOf(CounterOn.All))

  }
}
