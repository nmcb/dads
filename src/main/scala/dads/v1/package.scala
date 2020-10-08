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
    def fromString(string: String): MessageId =
      UUID.fromString(string)
  }

  case class SourceId(uuid: UUID) {
    override def toString: String =
      uuid.toString
  }

  object SourceId {
    // TODO we should treat inbound SourceIds as namespaced names, i.e.
    //      UUID.nameUUIDFromBytes(NameSpaced(name))
    def fromName(name: String): SourceId =
      SourceId(UUID.fromString(name))
  }

  implicit class UUIDOps(uuid: UUID) {
    def toSourceId =
      SourceId(uuid)
  }

  // TODO squants
  type NaturalUnit = String
}
