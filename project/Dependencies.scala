import sbt.*

object Dependencies {

  val micrometerVersion = "1.17.1"
  val zioVersion = "2.1.26"

  val commonScalacOptions = Seq(
    "-Wunused:imports", // предупреждения о неиспользованных import-ах
    "-Wconf:msg=unused import:e", // warnings → errors
    "-Wconf:msg=not be exhaustive:e", // неполный match → error
  )

  val common: Seq[ModuleID] = Seq(
    "dev.zio" %% "zio" % zioVersion,
    "dev.zio" %% "zio-http" % "3.11.4",
    "dev.zio" %% "zio-cache" % "0.3.0",
    "com.github.pureconfig" %% "pureconfig-core" % "0.17.10",
    "ch.qos.logback" % "logback-classic" % "1.6.3",
    "nl.vroste" %% "rezilience" % "0.10.5",
    "io.micrometer" % "micrometer-core" % micrometerVersion,
    "io.micrometer" % "micrometer-registry-prometheus" % micrometerVersion,
    "org.scalamock" %% "scalamock-zio" % "7.6.0" % Test,
  )
}
