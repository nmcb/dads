/*
 * This is free and unencumbered software released into the public domain.
 */

object Dependencies {

  import sbt._


  // VERSIONS

  val akkaVersion                     = "2.6.8"
  val akkaAlpakkaVersion              = "2.0.1"
  val logbackVersion                  = "1.2.3"
  val catsCoreVersion                 = "2.1.1"
  val kindProjectorPluginVersion      = "0.11.0"
  val akkaPersistenceCassandraVersion = "1.0.1"
  val datastaxCassandraDriverVersion  = "4.9.0"

  val scalaTestVersion  = "3.1.1"
  val scalaCheckVersion = "1.14.1"

  // LIBRARIES

  lazy val platformDeps: Seq[ModuleID] =
    Seq( "com.typesafe.akka"  %% "akka-actor-typed"             % akkaVersion
       , "com.typesafe.akka"  %% "akka-persistence-cassandra"   % akkaPersistenceCassandraVersion
       , "com.datastax.oss"   %  "java-driver-core"             % datastaxCassandraDriverVersion
       , "com.datastax.oss"   %  "java-driver-query-builder"    % datastaxCassandraDriverVersion
       )

  lazy val monitoringDeps: Seq[ModuleID] =
    Seq( "com.typesafe.akka"  %% "akka-slf4j"                   % akkaVersion
       , "ch.qos.logback"     %  "logback-classic"              % logbackVersion
       )

  lazy val testUtilDeps: Seq[ModuleID] =
    Seq( "org.scalatest"      %% "scalatest"                    % scalaTestVersion
       , "org.scalacheck"     %% "scalacheck"                   % scalaCheckVersion
       )

  lazy val bumpedAkkaRuntimeDeps: Seq[ModuleID] =
    Seq( "com.typesafe.akka"  %% "akka-persistence"
       , "com.typesafe.akka"  %% "akka-discovery"
       , "com.typesafe.akka"  %% "akka-remote"
       , "com.typesafe.akka"  %% "akka-cluster"
       , "com.typesafe.akka"  %% "akka-persistence-query"
       , "com.typesafe.akka"  %% "akka-coordination"
       , "com.typesafe.akka"  %% "akka-cluster-tools"
       ).map(_ % akkaVersion)

  lazy val bumpedGrpcDeps: Seq[ModuleID] =
    Seq( "com.typesafe.akka"  %% "akka-protobuf"                % akkaVersion
       , "com.typesafe.akka"  %% "akka-stream"                  % akkaVersion
       )

  lazy val testDeps: Seq[ModuleID] =
    testUtilDeps.map(_ % "test, it")


  // PLUGINS

  lazy val KindProjector: ModuleID =
    "org.typelevel" %% "kind-projector"  %  kindProjectorPluginVersion cross CrossVersion.full

}
