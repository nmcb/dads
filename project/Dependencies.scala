/*
 * This is free and unencumbered software released into the public domain.
 */

object Dependencies {

  import sbt._

  // VERSIONS

  val akkaVersion                     = "2.6.8"
  val logbackVersion                  = "1.2.3"
  val catsCoreVersion                 = "2.1.1"
  val kindProjectorPluginVersion      = "0.11.0"
  val scalaTestVersion                = "3.1.1"

  // LIBRARIES

  lazy val platformDeps: Seq[ModuleID] =
    Seq( "com.typesafe.akka"  %% "akka-actor-typed"  % akkaVersion
       )

  lazy val bumpGrpcDeps: Seq[ModuleID] =
    Seq( "com.typesafe.akka"  %% "akka-discovery"    % akkaVersion
       , "com.typesafe.akka"  %% "akka-protobuf"     % akkaVersion
       , "com.typesafe.akka"  %% "akka-stream"       % akkaVersion
       )

  lazy val monitoringDeps: Seq[ModuleID] =
    Seq(
      "com.typesafe.akka"  %% "akka-slf4j"       % akkaVersion,
      "ch.qos.logback"     %  "logback-classic"  % logbackVersion
    )

  lazy val testUtilDeps: Seq[ModuleID] =
    Seq( "org.scalatest"      %% "scalatest" % scalaTestVersion )

  lazy val testDeps: Seq[ModuleID] =
    testUtilDeps.map(_ % "test,it")


  // PLUGINS

  lazy val KindProjectorPlugin: ModuleID =
    "org.typelevel"  %% "kind-projector"  %  kindProjectorPluginVersion cross CrossVersion.full
}
