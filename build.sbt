import zio.sbt.{ZioSbtCiPlugin, ZioSbtEcosystemPlugin}

scalaVersion := "3.9.0"
ThisBuild / name := "cf-analysis"

inThisBuild(
  List(
    ciEnabledBranches := Seq("master"),
  ),
)

Global / excludeLintKeys ++= Set(
  Debian / executableScriptName,
  Debian / sourceDirectory,
  Rpm / daemonStdoutLogFile,
  Rpm / executableScriptName,
  Rpm / name,
  Rpm / sourceDirectory,
  Universal / executableScriptName,
  UniversalDocs / name,
  UniversalSrc / name,
)

lazy val root = rootProject
  .enablePlugins(ZioSbtEcosystemPlugin, ZioSbtCiPlugin, JavaAppPackaging, DockerPlugin)
  .settings(stdSettings(Some("cf-analysis"), Some("cf")))
  .settings(enableZIO())
  .settings(
    scalacOptions ++= Dependencies.commonScalacOptions,
    libraryDependencies ++= Dependencies.common,
    Compile / mainClass := Some("Main"),
  )
  .settings(ScaladocSettings.settings("cf-analysis"))
  .settings(DockerSettings.settings)
