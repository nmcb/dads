/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1.transport

trait Decoder[M <: scalapb.GeneratedMessage, A] {
  // FIXME Add validation in result
  def decode(msg: M): A
}