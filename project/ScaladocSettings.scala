import sbt.*
import sbt.Keys.*

/** Настройки генерации Scaladoc */
object ScaladocSettings {

  def settings(projectName: String): Seq[Setting[?]] = Seq(
    // Возвращаем источники для doc (иначе scaladoc получает пустой список)
    Compile / doc / sources := (Compile / sources).value,

    // Обходим баг кэширования scalacOptions в sbt 2.x (issue #9889).
    // Заодно фильтруем опции, которые scaladoc не понимает (-Wconf, -Wunused)
    Compile / doc / scalacOptions := Def.uncached {
      val filtered = (Compile / scalacOptions).value.filterNot { opt =>
        opt.startsWith("-Wconf") || opt.startsWith("-Wunused")
      }
      filtered ++ Seq(
        "-project",
        projectName,
        "-project-version",
        version.value,
        "-doc-footer",
        s"$projectName ${version.value} © $projectName",
      )
    },
  )
}
