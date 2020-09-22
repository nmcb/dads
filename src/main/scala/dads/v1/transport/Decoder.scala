/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1
package transport

import cats.data._

abstract class InboundError(val message: String) extends Product with Serializable

trait Decoder[M <: scalapb.GeneratedMessage, A] {
  def decode(msg: M): ValidatedNec[InboundError,A]
}