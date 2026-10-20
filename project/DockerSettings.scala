import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.*
import com.typesafe.sbt.packager.Keys.{maintainer, packageName}
import sbt.*
import sbt.Keys.*

/** Настройки Docker-образа. */
object DockerSettings {

  val settings: Seq[Setting[?]] = Seq(
    dockerBaseImage := "eclipse-temurin:17-jre-jammy",
    dockerEntrypoint := Seq(
      "bin/cf-analysis",
      "-J-XX:MaxRAMPercentage=75.0",
      "-J-XX:+UseZGC",
      "-J-XX:+UseContainerSupport",
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
}
