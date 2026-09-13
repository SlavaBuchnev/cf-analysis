import scala.concurrent.duration.FiniteDuration
import zio.{ZIO, ZLayer}

import pureconfig.{ConfigReader, ConfigSource}

case class App(
    contestIds: List[Int],
    handles: List[String],
    pollInterval: FiniteDuration,
    statusCount: Int,
    httpServer: HttpServerConfig,
    groupCode: Option[String],
    codeforces: Option[CodeforcesAuthConfig],
) derives ConfigReader

case class HttpServerConfig(host: String, port: Int)

case class CodeforcesAuthConfig(
    apiKey: String,
    apiSecret: String,
) derives ConfigReader

object App {
  val layer: ZLayer[Any, Throwable, App] = ZLayer.fromZIO(ZIO.attempt(ConfigSource.default.at("app").loadOrThrow[App]))
}
