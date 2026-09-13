import zio.http.{handler, Method, Request, Response, Routes}

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

object MetricsRoutes {
  def routes(registry: PrometheusMeterRegistry): Routes[Any, Response] =
    Routes(
      Method.GET / "metrics" -> handler { (_: Request) =>
        Response.text(registry.scrape())
      },
    )
}
