/*
 * This is free and unencumbered software released into the public domain.
 */

import Options._
import Dependencies._
import Workarounds._
import sbtassembly.MergeStrategy

ThisBuild / scalaVersion     := "2.13.3"
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


val customMergeStrategy: String => MergeStrategy = {
  case PathList("module-info.class") =>
    MergeStrategy.concat
  case PathList("META-INF", "io.netty.versions.properties") =>
    MergeStrategy.first
  case s =>
    MergeStrategy.defaultMergeStrategy(s)
}

def assemblySettings = Seq(
  // Skip tests during assembly
  assembly / test := {},
  // Override to remove the default '-assembly' infix
  assembly / assemblyJarName := "aurum-dads.jar",
  // Use the customMergeStrategy in your settings
  assembly / assemblyMergeStrategy := customMergeStrategy,
  assembly / mainClass := Some("dads.v1.Main")
)
