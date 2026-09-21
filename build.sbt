import zio.sbt.ZioSbtCiPlugin
import zio.sbt.ZioSbtEcosystemPlugin

val micrometerVersion = "1.17.1"
val zioVersion = "2.1.26"

scalaVersion := "3.9.0"
ThisBuild / name := "cf-analysis"

inThisBuild(
  List(
    ciEnabledBranches := Seq("master"),
  ),
)

lazy val root = rootProject
  .enablePlugins(ZioSbtEcosystemPlugin, ZioSbtCiPlugin)
  .settings(stdSettings(Some("cf-analysis"), Some("cf")))
  .settings(enableZIO())
  .settings(
    scalacOptions ++= Seq(
      "-Wunused:imports", // выдает предупрждения о неиспользованых  importов
      "-Wconf:msg=unused import:e", // превращает warnings в errors
      "-Wconf:msg=not be exhaustive:e", // не полный match => errors
    ),
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-http" % "3.11.4",
      "com.github.pureconfig" %% "pureconfig-core" % "0.17.10",
      "ch.qos.logback" % "logback-classic" % "1.6.3",
      "nl.vroste" %% "rezilience" % "0.10.5",
      "io.micrometer" % "micrometer-core" % micrometerVersion,
      "io.micrometer" % "micrometer-registry-prometheus" % micrometerVersion,
    ),
  )
