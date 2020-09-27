/*
 * This is free and unencumbered software released into the public domain.
 */

package dads

package object v1 {

  import java.util._

  type MessageId = UUID

  object MessageId {
    def fromString(string: String): SourceId =
      UUID.fromString(string)
  }

  type SourceId  = UUID

  object SourceId {
    def fromName(name: String): SourceId =
      UUID.nameUUIDFromBytes(s"dads.v1.$name".toArray.map(_.toByte))
  }


  // TODO squants
  type NaturalUnit = String
}
