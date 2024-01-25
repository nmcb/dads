/*
 * This is free and unencumbered software released into the public domain.
 */

object Workarounds {

  import sbt._
  import sbt.Keys._

  /** FIXME AkkaGrpcPlugin generates comments that breaks scaladoc during the sbt publish phase */
  lazy val onPublishMaskDocumentation: Setting[Task[Seq[File]]] =
    (Compile / doc / sources) := Seq.empty
}
