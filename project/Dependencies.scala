/*
 * This is free and unencumbered software released into the public domain.
 */

object Dependencies {

  import sbt._


  // VERSIONS

  val akkaVersion                     = "2.6.9"
  val akkaAlpakkaVersion              = "2.0.1"
  val logbackVersion                  = "1.5.10"
  val catsCoreVersion                 = "2.12.0"
  val kindProjectorPluginVersion      = "0.13.3"
  val akkaPersistenceCassandraVersion = "1.0.1"
  val datastaxCassandraDriverVersion  = "4.17.0"
  val scalaTestVersion                = "3.2.19"
  val scalaCheckVersion               = "1.18.0"

  // LIBRARIES

  lazy val platformDeps: Seq[ModuleID] =
    Seq( "com.typesafe.akka"  %% "akka-actor-typed"           % akkaVersion
       , "com.typesafe.akka"  %% "akka-persistence-cassandra" % akkaPersistenceCassandraVersion
       , "com.datastax.oss"   %  "java-driver-core"           % datastaxCassandraDriverVersion
       , "com.datastax.oss"   %  "java-driver-query-builder"  % datastaxCassandraDriverVersion
       , "org.typelevel"      %% "cats-core"                  % catsCoreVersion
       )

  lazy val monitoringDeps: Seq[ModuleID] =
    Seq( "com.typesafe.akka"  %% "akka-slf4j"      % akkaVersion
       , "ch.qos.logback"     %  "logback-classic" % logbackVersion
       )

  lazy val explicateAkkaRuntimeDeps: Seq[ModuleID] =
    Seq( "com.typesafe.akka"  %% "akka-persistence"
       , "com.typesafe.akka"  %% "akka-discovery"
       , "com.typesafe.akka"  %% "akka-remote"
       , "com.typesafe.akka"  %% "akka-cluster"
       , "com.typesafe.akka"  %% "akka-persistence-query"
       , "com.typesafe.akka"  %% "akka-coordination"
       , "com.typesafe.akka"  %% "akka-cluster-tools"
       ).map(_ % akkaVersion)

  lazy val explicateAkkaGrpcDeps: Seq[ModuleID] =
    Seq( "com.typesafe.akka"  %% "akka-protobuf"
       , "com.typesafe.akka"  %% "akka-stream"
       ).map(_ % akkaVersion)

  lazy val testDeps: Seq[ModuleID] =
    Seq( "org.scalatest"      %% "scalatest"                % scalaTestVersion
       , "org.scalacheck"     %% "scalacheck"               % scalaCheckVersion
       , "com.typesafe.akka"  %% "akka-actor-testkit-typed" % akkaVersion
       ).map(_ % "test, it")

  // PLUGINS

  lazy val KindProjector: ModuleID =
    "org.typelevel" %% "kind-projector"  %  kindProjectorPluginVersion cross CrossVersion.full

}
