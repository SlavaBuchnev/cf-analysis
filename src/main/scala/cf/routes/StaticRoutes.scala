package cf.routes

import zio.*
import zio.http.*

object StaticRoutes {

  val routes: Routes[Any, Response] =
    Routes(
      Method.GET / trailing -> handler { (path: Path, _: Request) =>
        val clean = path.toString.stripPrefix("/")
        val file = if (clean.isEmpty) "static/index.html" else s"static/$clean"

        readResource(file) match {
          // 1. Файл существует — отдаём его
          case Some(bytes) =>
            Response(
              status = Status.Ok,
              headers = Headers(Header.ContentType(mediaType(file))),
              body = Body.fromArray(bytes),
            )

          // 2. Файла нет, но в пути есть расширение — это ассет, 404
          case None if clean.contains('.') =>
            Response.status(Status.NotFound)

          // 3. Файла нет, путь без расширения — SPA-fallback на index.html
          case None =>
            readResource("static/index.html") match {
              case Some(bytes) =>
                Response(
                  status = Status.Ok,
                  headers = Headers(Header.ContentType(MediaType.text.html)),
                  body = Body.fromArray(bytes),
                )
              case None =>
                Response.status(Status.NotFound)
            }
        }
      },
    )

  private def readResource(path: String): Option[Array[Byte]] =
    Option(getClass.getClassLoader.getResourceAsStream(path))
      .map { is =>
        try is.readAllBytes()
        finally is.close()
      }

  private def mediaType(path: String): MediaType = {
    val ext = path.substring(path.lastIndexOf('.') + 1).toLowerCase
    ext match {
      case "html" | "htm" => MediaType.text.html
      case "css" => MediaType.text.css
      case "js" | "mjs" => MediaType.application.javascript
      case "json" => MediaType.application.json
      case "png" => MediaType.image.png
      case "jpg" | "jpeg" => MediaType.image.jpeg
      case "svg" => MediaType.image.`svg+xml`
      case _ => MediaType.application.`octet-stream`
    }
  }
}
