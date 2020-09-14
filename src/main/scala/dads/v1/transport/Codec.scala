/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package transport

import java.time.Instant
import java.util.UUID


import transport.grpc.v1._

object Codec {

  implicit class DecoderOps[M](msg: M) {

    def as[A: Decoder[M, *]]: A =
      implicitly[Decoder[M, A]].decode(msg)
  }

  implicit val measurementIndDecoder: Decoder[MeasurementDataInd, Update] =
    ind => Update(ind.messageId, ind.measurements.flatMap(_.as[Seq[Measurement]]))

  implicit val measurementDataDecoder: Decoder[MeasurementData, Seq[Measurement]] =
    data =>
      data.data.map(v =>
          // FIXME Add validation
          Measurement( UUID.fromString(data.sourceId)
                     , Instant.ofEpochMilli(v.timestamp)
                     , v.value.get.getDecimal.toLong
                     , data.unit
          ))
}
