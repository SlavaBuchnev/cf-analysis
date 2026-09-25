package cf.routes

import zio._
import zio.http._
import zio.test._

import cf.utils.RoutesSpec
import io.micrometer.core.instrument.Counter
import io.micrometer.prometheusmetrics.{PrometheusConfig, PrometheusMeterRegistry}

object MetricsRoutesSpec extends RoutesSpec[PrometheusMeterRegistry] {

  override def routesUnderTest: Routes[PrometheusMeterRegistry, Response] =
    MetricsRoutes.routes

  override def envLayer: ULayer[PrometheusMeterRegistry] =
    ZLayer.succeed(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT))

  def spec: Spec[TestEnvironment & Scope, Any] =
    suite("MetricsRoutesSpec")(
      successTests,
      notFoundTests,
    )

  private def successTests = suite("GET /metrics")(
    testWithRoutes("returns 200 with scrape output") { (registry, routes) =>
      Counter.builder("test_metric_total").register(registry).increment()
      for {
        response <- runRoute(routes, get("/metrics"))
        body <- bodyOf(response)
      } yield assertTrue(
        response.status == Status.Ok,
        body.contains("test_metric_total"),
      )
    },
    testWithRoutes("returns 200 with empty registry") { (_, routes) =>
      for {
        response <- runRoute(routes, get("/metrics"))
      } yield assertTrue(response.status == Status.Ok)
    },
  )

  private def notFoundTests = suite("unknown routes")(
    testWithRoutes("returns 404 for unknown path") { (_, routes) =>
      for {
        response <- runRoute(routes, get("/unknown"))
      } yield assertTrue(response.status == Status.NotFound)
    },
  )
}
