package cf.routes

import scala.concurrent.duration.DurationInt

import zio.http.*
import zio.test.*
import zio.{Scope, ULayer, ZIO, ZLayer}

import cf.codeforces.CodeforcesCache
import cf.configs.CorsConfig

import io.micrometer.prometheusmetrics.{PrometheusConfig, PrometheusMeterRegistry}
import org.scalamock.ziotest.ScalamockZIOSpec

/** Тесты CORS-обработки всех роутов на уровне `HttpRoutes`.
  *
  * Проверяются: выставление `Access-Control-Allow-Origin` для разрешённого origin, отказ неразрешённому origin (`403` /
  * preflight `404`), корректный preflight (`OPTIONS` → `204` с заголовками), отсутствие CORS-заголовков при выключенной
  * конфигурации. В качестве эндпоинта используется `GET /ping` — он не трогает кэш, поэтому мок без ожиданий.
  */
object HttpRoutesSpec extends ScalamockZIOSpec {

  private val allowedOrigin = "http://localhost:3000"
  private val disallowedOrigin = "https://evil.example.com"

  private val corsEnabled = CorsConfig(
    enabled = true,
    allowedOrigins = List(allowedOrigin),
    allowedMethods = List("GET"),
    allowedHeaders = List("Content-Type"),
    maxAge = 30.minutes,
  )

  private val corsDisabled = corsEnabled.copy(enabled = false)

  private def registryLayer: ULayer[PrometheusMeterRegistry] =
    ZLayer.succeed(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT))

  private def testWithCors(label: String, cors: CorsConfig)(
      f: Routes[Any, Response] => ZIO[Scope, Throwable, TestResult],
  ): Spec[TestEnvironment & Scope, Any] =
    test(label) {
      ZIO.scoped {
        for {
          httpRoutes <- ZIO.service[HttpRoutes]
          result <- f(httpRoutes.routes)
        } yield result
      }
    }.provide(HttpRoutes.layer, ZLayer.succeed(cors), registryLayer, mock[CodeforcesCache])

  private def getWithOrigin(path: String, origin: String): Request =
    Request
      .get(URL.decode(path).toOption.get)
      .addHeader(Header.Origin.parse(origin).toOption.get)

  private def preflight(path: String, origin: String, requestMethod: Method): Request =
    Request
      .options(URL.decode(path).toOption.get)
      .addHeader(Header.Origin.parse(origin).toOption.get)
      .addHeader(Header.AccessControlRequestMethod(requestMethod))

  private def run(routes: Routes[Any, Response], request: Request): ZIO[Scope, Throwable, Response] =
    routes.runZIO(request)

  private def allowOriginOf(response: Response): Option[String] =
    response.header(Header.AccessControlAllowOrigin).map(Header.AccessControlAllowOrigin.render)

  def spec: Spec[TestEnvironment & Scope, Any] =
    suite("HttpRoutesSpec")(
      suite("CORS enabled")(
        testWithCors("adds allow-origin header for allowed origin", corsEnabled) { routes =>
          for {
            response <- run(routes, getWithOrigin("/ping", allowedOrigin))
          } yield assertTrue(
            response.status == Status.Ok,
            allowOriginOf(response).contains(allowedOrigin),
          )
        },
        testWithCors("rejects request from disallowed origin with 403", corsEnabled) { routes =>
          for {
            response <- run(routes, getWithOrigin("/ping", disallowedOrigin))
          } yield assertTrue(
            response.status == Status.Forbidden,
            allowOriginOf(response).isEmpty,
          )
        },
        testWithCors("passes request without Origin header", corsEnabled) { routes =>
          for {
            response <- run(routes, Request.get(URL.decode("/ping").toOption.get))
          } yield assertTrue(
            response.status == Status.Ok,
            allowOriginOf(response).isEmpty,
          )
        },
        testWithCors("handles preflight for allowed origin", corsEnabled) { routes =>
          for {
            response <- run(routes, preflight("/ping", allowedOrigin, Method.GET))
            allowMethods = response
              .header(Header.AccessControlAllowMethods)
              .map(Header.AccessControlAllowMethods.render)
          } yield assertTrue(
            response.status == Status.NoContent,
            allowOriginOf(response).contains(allowedOrigin),
            allowMethods.contains("GET"),
            response.header(Header.AccessControlMaxAge).isDefined,
          )
        },
        testWithCors("preflight from disallowed origin is not allowed", corsEnabled) { routes =>
          for {
            response <- run(routes, preflight("/ping", disallowedOrigin, Method.GET))
          } yield assertTrue(response.status == Status.NotFound)
        },
      ),
      suite("CORS disabled")(
        testWithCors("does not add CORS headers", corsDisabled) { routes =>
          for {
            response <- run(routes, getWithOrigin("/ping", allowedOrigin))
          } yield assertTrue(
            response.status == Status.Ok,
            allowOriginOf(response).isEmpty,
          )
        },
      ),
    )
}
