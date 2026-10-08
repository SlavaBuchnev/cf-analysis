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
    scalacOptions ++= Seq(
      "-Wunused:imports", // выдает предупрждения о неиспользованых  importов
      "-Wconf:msg=unused import:e", // превращает warnings в errors
      "-Wconf:msg=not be exhaustive:e", // не полный match => errors
    ),
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-http" % "3.11.4",
      "dev.zio" %% "zio-cache" % "0.3.0",
      "com.github.pureconfig" %% "pureconfig-core" % "0.17.10",
      "ch.qos.logback" % "logback-classic" % "1.6.3",
      "nl.vroste" %% "rezilience" % "0.10.5",
      "io.micrometer" % "micrometer-core" % micrometerVersion,
      "io.micrometer" % "micrometer-registry-prometheus" % micrometerVersion,
      "org.scalamock" %% "scalamock-zio" % "7.6.0" % Test,
    ),
    Compile / mainClass := Some("Main"),
    dockerBaseImage := "eclipse-temurin:17-jre-jammy",
    dockerEntrypoint := Seq(
      "bin/cf-analysis",
      "-J-XX:MaxRAMPercentage=75.0", // Использует 75% от лимита памяти контейнера
      "-J-XX:+UseZGC", // Низколатентный сборщик мусора
      "-J-XX:+UseContainerSupport", // Автоматическое определение лимитов
    ),
    dockerExposedPorts := Seq(8080),
    Docker / packageName := s"${sys.env.getOrElse("DOCKER_USERNAME", "cf-analysis")}/cf-analysis",
    Docker / version := sys.env.getOrElse("DOCKER_TAG", "latest"),
    Docker / maintainer := "Slava Buchnev <slavabuchnev5@gmail.com>",
    dockerLabels := Map(
      "maintainer" -> maintainer.value,
      "version" -> version.value,
    ),
  )
