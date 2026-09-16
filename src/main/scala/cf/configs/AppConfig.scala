package cf.configs

import scala.concurrent.duration.FiniteDuration
import zio.{ZIO, ZLayer}

import pureconfig.{ConfigReader, ConfigSource}

case class AppConfig(
    contestIds: List[Int],
    handles: List[String],
    pollInterval: FiniteDuration,
    statusCount: Int,
    httpServer: HttpServerConfig,
    groupCode: Option[String],
    codeforces: Option[CfAuthConfig],
) derives ConfigReader

object AppConfig {
  val layer: ZLayer[Any, Throwable, AppConfig] =
    ZLayer.fromZIO(ZIO.attempt(ConfigSource.default.at("app").loadOrThrow[AppConfig]))
}
