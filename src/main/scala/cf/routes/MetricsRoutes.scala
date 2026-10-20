package cf.routes

import zio.http.*
import zio.ZIO

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

/** Роуты для отдачи метрик приложения в формате Prometheus.
  *
  * Эндпоинт `GET /metrics` возвращает текущий снимок метрик (scrape), собранный
  * [[io.micrometer.prometheusmetrics.PrometheusMeterRegistry]].
  */
object MetricsRoutes {
  val routes: Routes[PrometheusMeterRegistry, Response] =
    Routes(
      Method.GET / "metrics" ->
        handler { (_: Request) =>
          ZIO.serviceWith[PrometheusMeterRegistry](r => Response.text(r.scrape()))
        },
    )
}
