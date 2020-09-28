/*
 * This is free and unencumbered software released into the public domain.
 */

package dads

package object v1 {

  def NameSpaced(name: String): Array[Byte] =
    s"urn:dads.v1:$name".toArray.map(_.toByte)


  import java.util._

  type MessageId = UUID

  object MessageId {
    def fromString(string: String): SourceId =
      UUID.fromString(string)
  }

  type SourceId  = UUID

  object SourceId {
    def fromName(name: String): SourceId =
      UUID.nameUUIDFromBytes(NameSpaced(name))
  }


  // TODO squants
  type NaturalUnit = String
}
