import sbt.*
import sbt.Keys.*

object ScaladocSettings {

  /** Настройки для генерации Scaladoc. */
  def settings(projectName: String): Seq[Setting[?]] = Seq(
    Compile / doc / scalacOptions ++= Seq(
      "-project",
      projectName,
      "-project-version",
      version.value,
      "-doc-footer",
      s"$projectName ${version.value} © $projectName",
    ),
    // Отключаем публикацию scaladoc-артефакта, если это необходимо
    Compile / packageDoc / publishArtifact := false,
  )
}
