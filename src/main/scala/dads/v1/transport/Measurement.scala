/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package transport

import java.time._

case class Measurement( sourceId  : SourceId
                      , timestamp : Instant
                      , reading   : Long
                      , unit      : NaturalUnit
                      ) extends ProtoBuffed

object Measurement {

  implicit val measurementInstantDescendingOrdering: Ordering[Measurement] =
    (lhs, rhs) => lhs.timestamp.compareTo(rhs.timestamp)
}
