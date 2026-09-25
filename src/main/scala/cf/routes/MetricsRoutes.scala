package cf.routes

import zio.http.*
import zio.ZIO

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

object MetricsRoutes {
  val routes: Routes[PrometheusMeterRegistry, Response] =
    Routes(
      Method.GET / "metrics" ->
        handler { (_: Request) =>
          ZIO.serviceWith[PrometheusMeterRegistry](r => Response.text(r.scrape()))
        },
    )
}
