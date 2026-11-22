package cf.configs

import scala.concurrent.duration.FiniteDuration

import zio.Duration
import zio.http.Header.*
import zio.http.Method
import zio.http.Middleware.CorsConfig as ZioCorsConfig

import pureconfig.ConfigReader

/** Конфигурация CORS-политики HTTP-API: список разрешённых origins, методы и заголовки, кэширование preflight.
  *
  * При `enabled = false` middleware не применяется и CORS-заголовки не выставляются (поведение по умолчанию — SPA
  * отдаётся с того же origin, что и API). Разрешение origin — точное совпадение его строкового представления (например,
  * `http://localhost:3000`) со значением из `allowedOrigins`. Конвертация в конфигурацию middleware zio-http — через
  * [[toMiddlewareConfig]], применяется в `cf.routes.HttpRoutes`.
  */
final case class CorsConfig(
    enabled: Boolean,
    allowedOrigins: List[String],
    allowedMethods: List[String],
    allowedHeaders: List[String],
    maxAge: FiniteDuration,
) derives ConfigReader {

  /** Конфигурация CORS-middleware zio-http. */
  def toMiddlewareConfig: ZioCorsConfig =
    ZioCorsConfig(
      allowedOrigin = origin =>
        if (allowedOrigins.contains(Origin.render(origin))) Some(AccessControlAllowOrigin.Specific(origin))
        else None,
      allowedMethods = AccessControlAllowMethods(allowedMethods.map(Method.fromString)*),
      allowedHeaders = AccessControlAllowHeaders(allowedHeaders*),
      maxAge = Some(AccessControlMaxAge(Duration.fromScala(maxAge))),
    )
}
