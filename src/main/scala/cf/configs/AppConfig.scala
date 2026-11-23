package cf.configs

import zio.{ZIO, ZLayer}

import pureconfig.{ConfigReader, ConfigSource}

/** Корневая конфигурация приложения, загружаемая из `application.conf` (секция `app`). */
case class AppConfig(
    httpServer: HttpServerConfig,
    rateLimiter: RateLimiterConfig,
    cfApi: CfAuthConfig,
    cache: CacheConfig,
    cors: CorsConfig,
) derives ConfigReader

/** Загрузка корневой конфигурации `application.conf` и публичные слои-проекции отдельных её секций. */
object AppConfig {

  /** Полная конфигурация приложения (приватная, используется через проекции ниже). */
  private val layer: ZLayer[Any, Throwable, AppConfig] =
    ZLayer.fromZIO(ZIO.attempt(ConfigSource.default.at("app").loadOrThrow[AppConfig]))

  val rateLimiterCfgLayer: ZLayer[Any, Throwable, RateLimiterConfig] = layer.project(_.rateLimiter)

  val cfApiCfgLayer: ZLayer[Any, Throwable, CfAuthConfig] = layer.project(_.cfApi)

  val httpServerCfgLayer: ZLayer[Any, Throwable, HttpServerConfig] = layer.project(_.httpServer)

  val cacheCfgLayer: ZLayer[Any, Throwable, CacheConfig] = layer.project(_.cache)

  val corsCfgLayer: ZLayer[Any, Throwable, CorsConfig] = layer.project(_.cors)
}
