package cf.routes

import zio.*
import zio.http.*

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object StaticRoutes {

  private val staticPrefix = "static"
  private val indexFile = s"$staticPrefix/index.html"
  private val notFound: Response = Response.status(Status.NotFound)

  val routes: Routes[Any, Response] =
    Routes(
      Method.GET / trailing -> handler { (path: Path, _: Request) =>
        serve(path.toString)
      },
    )

  private def serve(rawPath: String): Response =
    safeStaticPath(rawPath) match {
      case Some(file) =>
        readResource(file) match {
          case Some(bytes) => ok(bytes, mediaTypeOf(file))
          case None => serveIndex()
        }
      case None => serveIndex()
    }

  private def safeStaticPath(rawPath: String): Option[String] = {
    val decoded = decodePath(rawPath).stripPrefix("/")
    if (decoded.isEmpty) Some(indexFile)
    else {
      val segments = decoded.split('/').toList
      val hasInvalidSegment =
        segments.exists(seg => seg.isEmpty || seg == "." || seg == "..")

      if (hasInvalidSegment) None
      else Some(s"$staticPrefix/${segments.mkString("/")}")
    }
  }

  private def decodePath(path: String): String =
    URLDecoder.decode(path, StandardCharsets.UTF_8)

  private def serveIndex(): Response =
    readResource(indexFile)
      .map(ok(_, MediaType.text.html))
      .getOrElse(notFound)

  private def ok(bytes: Array[Byte], contentType: MediaType): Response =
    Response(
      status = Status.Ok,
      headers = Headers(Header.ContentType(contentType)),
      body = Body.fromArray(bytes),
    )

  private def readResource(path: String): Option[Array[Byte]] =
    Option(getClass.getClassLoader.getResourceAsStream(path))
      .map { is =>
        try is.readAllBytes()
        finally is.close()
      }

  private def mediaTypeOf(path: String): MediaType = {
    val ext = path.substring(path.lastIndexOf('.') + 1).toLowerCase
    ext match {
      case "html" => MediaType.text.html
      case "css" => MediaType.text.css
      case "js" => MediaType.application.javascript
      case _ => MediaType.application.`octet-stream`
    }
  }
}
