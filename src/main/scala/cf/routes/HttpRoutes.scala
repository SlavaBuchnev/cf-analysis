package cf.routes

import zio.http.{Middleware, Response, Routes}
import zio.{ZIO, ZLayer}

import cf.codeforces.CodeforcesCache
import cf.configs.CorsConfig

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

/** Все HTTP-роуты приложения, собранные в один `Routes[Any, Response]`.
  *
  * Объединяет [[ContestsRoutes]], [[MetricsRoutes]], [[HealthRoutes]] и [[StaticRoutes]], при включённой конфигурации
  * оборачивает всё дерево в CORS-middleware (`Middleware.cors`) и внедряет необходимое окружение (`CodeforcesCache`,
  * `PrometheusMeterRegistry` и `CorsConfig`).
  */
final case class HttpRoutes(routes: Routes[Any, Response])

object HttpRoutes {

  private def buildRoutes(cors: CorsConfig): Routes[CodeforcesCache & PrometheusMeterRegistry, Response] = {
    val all = ContestsRoutes.routes ++ MetricsRoutes.routes ++ HealthRoutes.routes ++ StaticRoutes.routes
    if (cors.enabled) all @@ Middleware.cors(cors.toMiddlewareConfig) else all
  }

  val layer: ZLayer[CodeforcesCache & PrometheusMeterRegistry & CorsConfig, Nothing, HttpRoutes] =
    ZLayer.fromZIO {
      ZIO.environment[CodeforcesCache & PrometheusMeterRegistry & CorsConfig].map { env =>
        HttpRoutes(buildRoutes(env.get[CorsConfig]).provideEnvironment(env))
      }
    }
}
