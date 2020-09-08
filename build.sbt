import Options._
import Dependencies._
import Workarounds._

ThisBuild / scalaVersion     := "2.13.3"
ThisBuild / version          := "0.0.1-SNAPSHOT"
ThisBuild / organization     := "nmcb"
ThisBuild / organizationName := "nmcb"

lazy val dads =
  (project in file("."))
    .configs(IntegrationTest)
    .enablePlugins(AkkaGrpcPlugin, JavaAppPackaging)
    .settings( name                := "dads"
             , libraryDependencies ++= platformDeps ++ monitoringDeps ++ testDeps ++ bumpGrpcDeps ++ bumpAkkaTestDeps
             , scalacOptions       ++= hygienicScalacOps
             , Defaults.itSettings
             , onPublishMaskDocumentation
             , addCompilerPlugin(KindProjectorPlugin)
             )

dockerRepository              := Some("dads")
dockerBaseImage               := "java"
version            in Docker  := "latest"
dockerExposedPorts in Docker  := Seq(8080)
mainClass          in Compile := Some("dads.v1.Main")
