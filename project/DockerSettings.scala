import com.typesafe.sbt.packager.Keys.{maintainer, packageName}
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.*
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.*
import com.typesafe.sbt.packager.Keys.{
  daemonGroup,
  daemonGroupGid,
  daemonUser,
  daemonUserUid,
  executableScriptName,
  maintainer,
  packageName,
}
import sbt.*
import sbt.Keys.*

/** Настройки Docker-образа. */
object DockerSettings {

  val settings: Seq[Setting[?]] = Seq(
    dockerBaseImage := "eclipse-temurin:25.0.5_8-jre-noble",
    Docker / daemonUser := "app",
    Docker / daemonGroup := "app",
    Docker / daemonUserUid := Some("1001"),
    Docker / daemonGroupGid := Some("1001"),
    dockerEntrypoint := Seq(
      s"bin/${executableScriptName.value}",
      "-J-XX:MaxRAMPercentage=75.0",
      "-J-XX:+UseZGC",
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
