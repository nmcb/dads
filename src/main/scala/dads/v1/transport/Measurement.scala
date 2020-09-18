/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package transport

import java.time._

case class Update( messageId    : String
                 , measurements : Seq[Measurement]
                 ) extends ProtoBuffed

case class Measurement( sourceId  : SourceId
                      , timestamp : Instant
                      , value     : Long
                      , unit      : NaturalUnit
                      ) extends ProtoBuffed
