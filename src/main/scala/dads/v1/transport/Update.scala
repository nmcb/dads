/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package transport

case class Update( messageId: MessageId
                 , measurements: Seq[Measurement]
                 ) extends ProtoBuffed {

  def normalise: Update =
    copy(measurements = measurements.map(_.normalise))

  def measurementsBySource: Map[SourceId, Seq[Measurement]] =
    measurements.groupBy(_.sourceId)
}
