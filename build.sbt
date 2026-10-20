scalaVersion := "3.9.0"
ThisBuild / name := "cf-analysis"
ThisBuild / organization := "cf-analysis"

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
  .enablePlugins(JavaAppPackaging, DockerPlugin, GitHubPagesPlugin)
  .settings(
    scalacOptions ++= Dependencies.commonScalacOptions,
    libraryDependencies ++= Dependencies.common,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    Compile / mainClass := Some("Main"),
  )
  .settings(ScaladocSettings.settings("cf-analysis"))
  .settings(DockerSettings.settings)
