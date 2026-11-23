import sttp.client4.BackendOptions
import sttp.client4.httpclient.zio.{HttpClientZioBackend, SttpClient}
import zio.{ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer}

import cf.codeforces.{CodeforcesCache, CodeforcesClient, CodeforcesService}
import cf.configs.{AppConfig, CfAuthConfig}
import cf.metrics.MetricsCollector
import cf.routes.HttpRoutes
import cf.utils.RateLimiter
import cf.{AppLifecycle, HttpServer}

/** Точка входа в приложение.
  *
  * Запускает [[cf.AppLifecycle]] внутри scoped-контекста и собирает всё необходимое окружение (конфигурацию, метрики,
  * HTTP-роуты, кэш, клиент Codeforces, rate limiter) через [[zio.ZLayer]].
  */
object Main extends ZIOAppDefault {

  /** sttp-бэкенд (`java.net.http.HttpClient`) с connection timeout из `cf-api.http-client`. */
  private val sttpBackend: ZLayer[CfAuthConfig, Throwable, SttpClient] =
    ZLayer.scoped(
      ZIO.serviceWithZIO[CfAuthConfig](cfg =>
        HttpClientZioBackend.scoped(
          BackendOptions(
            connectionTimeout = cfg.httpClient.connectionTimeout,
            proxy = None,
          ),
        ),
      ),
    )

  override def run: ZIO[ZIOAppArgs, Any, Any] =
    ZIO
      .scoped(ZIO.serviceWithZIO[AppLifecycle](_.start))
      .provide(
        // configs
        AppConfig.rateLimiterCfgLayer,
        AppConfig.cfApiCfgLayer,
        AppConfig.httpServerCfgLayer,
        AppConfig.cacheCfgLayer,
        AppConfig.corsCfgLayer,
        // metrics
        MetricsCollector.layer,
        // routes
        HttpRoutes.layer,
        // lifecycle
        AppLifecycle.layer,
        // infrastructure
        sttpBackend,
        CodeforcesCache.layer,
        CodeforcesClient.layer,
        CodeforcesService.layer,
        HttpServer.layer,
        RateLimiter.layer,
      )
}
