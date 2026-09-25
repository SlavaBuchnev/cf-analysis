package cf.routes

import cf.codeforces.CodeforcesCache
import zio.{ZIO, ZLayer}
import zio.http.{Response, Routes}
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

final case class HttpRoutes(routes: Routes[Any, Response])

object HttpRoutes {
  private val routes: Routes[CodeforcesCache & PrometheusMeterRegistry, Response] =
    ContestsRoutes.routes ++ MetricsRoutes.routes

  val layer: ZLayer[CodeforcesCache & PrometheusMeterRegistry, Nothing, HttpRoutes] =
    ZLayer.fromZIO {
      ZIO.environment[CodeforcesCache & PrometheusMeterRegistry].map { env =>
        HttpRoutes(routes.provideEnvironment(env))
      }
    }
}
