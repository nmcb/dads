/*
 * This is free and unencumbered software released into the public domain.
 */

object Options {

  val hygienicScalacOps: Seq[String] =
    Seq( "-encoding", "utf8"
       , "-language:implicitConversions"
       , "-language:higherKinds"
       , "-language:existentials"
       , "-language:postfixOps"
       , "-deprecation"
       , "-unchecked"
       , "-Xfatal-warnings"
       , "-Xcheckinit"
       , "-Xlint:adapted-args"
       , "-Xlint:nullary-unit"
       , "-Wdead-code"
       )

}
