package cf.routes

import zio.http.{Response, Routes}
import zio.{ZIO, ZLayer}

import cf.codeforces.CodeforcesCache

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

/** Все HTTP-роуты приложения, собранные в один `Routes[Any, Response]`.
  *
  * Объединяет [[ContestsRoutes]], [[MetricsRoutes]] и [[HealthRoutes]] и внедряет необходимое окружение
  * (`CodeforcesCache` и `PrometheusMeterRegistry`).
  */
final case class HttpRoutes(routes: Routes[Any, Response])

object HttpRoutes {
  private val routes: Routes[CodeforcesCache & PrometheusMeterRegistry, Response] =
    ContestsRoutes.routes ++ MetricsRoutes.routes ++ HealthRoutes.routes ++ StaticRoutes.routes

  val layer: ZLayer[CodeforcesCache & PrometheusMeterRegistry, Nothing, HttpRoutes] =
    ZLayer.fromZIO {
      ZIO.environment[CodeforcesCache & PrometheusMeterRegistry].map { env =>
        HttpRoutes(routes.provideEnvironment(env))
      }
    }
}
