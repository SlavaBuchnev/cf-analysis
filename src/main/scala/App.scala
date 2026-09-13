import scala.concurrent.duration.FiniteDuration

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
  def load(): App = ConfigSource.default.at("app").loadOrThrow[App]
}
