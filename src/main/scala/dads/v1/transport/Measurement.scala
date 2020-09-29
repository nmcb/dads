/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package transport

import java.time._

case class Update( messageId    : MessageId
                 , measurements : Seq[Measurement]
                 ) extends ProtoBuffed {

  def measurementsBySource: Map[SourceId,Seq[Measurement]] =
    measurements.groupBy(_.sourceId)
}

case class Measurement( sourceId  : SourceId
                      , timestamp : Instant
                      , reading   : Long
                      , unit      : NaturalUnit
                      ) extends ProtoBuffed
