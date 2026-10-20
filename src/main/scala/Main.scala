import zio.{ZIO, ZIOAppArgs, ZIOAppDefault}
import zio.http.Client

import cf.{AppLifecycle, HttpServer}
import cf.codeforces.{CodeforcesCache, CodeforcesClient, CodeforcesService}
import cf.configs.AppConfig
import cf.metrics.MetricsCollector
import cf.routes.HttpRoutes
import cf.utils.RateLimiter

/** Точка входа в приложение.
  *
  * Запускает [[cf.AppLifecycle]] внутри scoped-контекста и собирает всё необходимое окружение (конфигурацию, метрики,
  * HTTP-роуты, кэш, клиент Codeforces, rate limiter) через [[zio.ZLayer]].
  */
object Main extends ZIOAppDefault {

  override def run: ZIO[ZIOAppArgs, Any, Any] =
    ZIO
      .scoped(ZIO.serviceWithZIO[AppLifecycle](_.start))
      .provide(
        // configs
        AppConfig.rateLimiterCfgLayer,
        AppConfig.cfApiCfgOptLayer,
        AppConfig.httpServerCfgLayer,
        AppConfig.cacheCfgLayer,
        // metrics
        MetricsCollector.layer,
        // routes
        HttpRoutes.layer,
        // lifecycle
        AppLifecycle.layer,
        // infrastructure
        Client.default,
        CodeforcesCache.layer,
        CodeforcesClient.layer,
        CodeforcesService.layer,
        HttpServer.layer,
        RateLimiter.layer,
      )
}
