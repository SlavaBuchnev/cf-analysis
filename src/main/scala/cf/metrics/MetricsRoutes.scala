package cf.metrics

import zio.http.*

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

object MetricsRoutes {
  def routes(registry: PrometheusMeterRegistry): Routes[Any, Response] =
    Routes(
      Method.GET / "metrics" -> handler { (_: Request) =>
        Response.text(registry.scrape())
      },
    )
}
