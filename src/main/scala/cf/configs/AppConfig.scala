package cf.configs

import zio.{ZIO, ZLayer}

import pureconfig.{ConfigReader, ConfigSource}

/** Корневая конфигурация приложения, загружаемая из `application.conf` (секция `app`). */
case class AppConfig(
    httpServer: HttpServerConfig,
    rateLimiter: RateLimiterConfig,
    cfApi: Option[CfAuthConfig],
    cache: CacheConfig,
) derives ConfigReader

object AppConfig {

  /** Полная конфигурация приложения (приватная, используется через проекции ниже). */
  private val layer: ZLayer[Any, Throwable, AppConfig] =
    ZLayer.fromZIO(ZIO.attempt(ConfigSource.default.at("app").loadOrThrow[AppConfig]))

  val rateLimiterCfgLayer: ZLayer[Any, Throwable, RateLimiterConfig] = layer.project(_.rateLimiter)

  val cfApiCfgOptLayer: ZLayer[Any, Throwable, Option[CfAuthConfig]] = layer.project(_.cfApi)

  val httpServerCfgLayer: ZLayer[Any, Throwable, HttpServerConfig] = layer.project(_.httpServer)

  val cacheCfgLayer: ZLayer[Any, Throwable, CacheConfig] = layer.project(_.cache)
}
