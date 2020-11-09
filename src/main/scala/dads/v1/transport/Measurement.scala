/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package transport

import java.time._

case class Measurement( sourceId  : SourceId
                      , timestamp : Instant
                      , reading   : Double
                      , unit      : NaturalUnit
                      ) extends ProtoBuffed
{
  def normalise: Measurement =
    unit match {
      case "kWh" => copy(unit = "μWh", reading = reading * 1.0E6)
      case "m³"  => copy(unit = "mm³", reading = reading * 1.0E9)
      case "Wh"  => this // FIXME convert Wh to J, see https://github.com/nmcb/dads/issues/14
      case _     => this
    }
}

object Measurement {

  implicit val measurementInstantDescendingOrdering: Ordering[Measurement] =
    (lhs, rhs) => lhs.timestamp.compareTo(rhs.timestamp)
}
