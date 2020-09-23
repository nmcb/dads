/*
 * This is free and unencumbered software released into the public domain.
 */

package dads.v1

package object transport {

  import cats.data._

  abstract class InboundError(val message: String)
    extends Product
      with Serializable

  type Nec[A] = NonEmptyChain[A]
  type Val[A] = ValidatedNec[InboundError,A]

  def tuple2Identity[A,B]: (A,B) => (A,B) =
    { case (a,b) => (a,b) }
}
