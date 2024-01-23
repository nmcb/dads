/*
 * This is free and unencumbered software released into the public domain.
 */

import Options._
import Dependencies._
import Workarounds._

ThisBuild / scalaVersion     := "2.13.12"
ThisBuild / version          := "0.1.1"
ThisBuild / organization     := "nmcb"
ThisBuild / organizationName := "nmcb"

lazy val dads =
  (project in file("."))
    .configs(IntegrationTest)
    .enablePlugins(AkkaGrpcPlugin, JavaAppPackaging)
    .settings( name                := "dads"

             , libraryDependencies ++= Seq( platformDeps
                                          , monitoringDeps
                                          , explicateAkkaGrpcDeps
                                          , explicateAkkaRuntimeDeps
                                          , testDeps
                                          ).flatten

             , scalacOptions       ++= hygienicScalacOps
             , Defaults.itSettings
             , onPublishMaskDocumentation
             , addCompilerPlugin(KindProjector)
             )

// FIXME Pending https://github.com/sbt/sbt/issues/5008
evictionWarningOptions in update :=
  EvictionWarningOptions
    .default
    .withWarnTransitiveEvictions(false)
    .withWarnDirectEvictions(false)
    .withWarnScalaVersionEviction(false)

dockerRepository              := Some("dads")
dockerBaseImage               := "java"
version            in Docker  := "latest"
dockerExposedPorts in Docker  := Seq(8080)
mainClass          in Compile := Some("dads.v1.Main")
