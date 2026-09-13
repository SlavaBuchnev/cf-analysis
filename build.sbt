scalaVersion := "3.9.0"

val micrometerVersion = "1.17.1"

lazy val root = rootProject
  .settings(
    name := "cf-analysis",
    scalacOptions ++= Seq(
      "-Wunused:imports", // выдает предупрждения о неиспользованых  importов
      "-Wconf:msg=unused import:e", // превращает warnings в errors
      "-Wconf:msg=not be exhaustive:e", // не полный match => errors
    ),
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "2.1.26",
      "dev.zio" %% "zio-http" % "3.11.4",
      "com.github.pureconfig" %% "pureconfig-core" % "0.17.10",
      "ch.qos.logback" % "logback-classic" % "1.6.3",
      "io.micrometer" % "micrometer-core" % micrometerVersion,
      "io.micrometer" % "micrometer-registry-prometheus" % micrometerVersion,
    ),
  )
