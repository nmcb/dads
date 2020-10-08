/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package test
package data

import java.util._

import org.scalacheck._

import scala.util.Random

trait ArbitrarySources {

  import Gen._
  import Arbitrary._

  implicit val arbitrarySourceId: Arbitrary[SourceId] =
    Arbitrary {
      if (Random.nextGaussian().abs < 0.0)
        arbitrary[UUID].map(_.toSourceId)
      else
        oneOf(Seq( "bf84271c-8d9b-4bca-a7ac-004af7b064cc"
                 , "5dee3bfa-2285-4a4f-9a11-d7fc9da72007"
                 , "9d859118-1d2c-49cd-b804-b750a2f4e48e"
                 , "5918c932-7c5d-495d-bea9-3881a682b9fd"
                 , "d3a51cad-5c6b-486a-b311-ee9a140c7086"
                 , "1dd3f705-a07e-4585-9cce-fee70f8c9c01"
                 , "929f8710-5f54-4def-9084-1cabd8ce017c"
                 , "4c497559-0287-4bbe-b065-70e2686858c8"
                 , "9d7d6bdf-248c-48d0-b8b6-2b3e5e44d350"
                 , "c2af9865-5e59-4034-8c6c-bead89d368ab"
        )).map(SourceId.fromName)
    }
}
