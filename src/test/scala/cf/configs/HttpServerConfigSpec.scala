package cf.configs

import scala.concurrent.duration.DurationInt
import zio.test.*

import com.typesafe.config.ConfigMemorySize

object HttpServerConfigSpec extends ConfigSpec[HttpServerConfig] {

  override protected val configPath: String = "http-server"

  override protected val validHocon: String =
    """http-server {
      |  host = "0.0.0.0"
      |  port = 8080
      |  max-header-size = 16KiB
      |  max-initial-line-length = 8KiB
      |  keep-alive = true
      |  idle-timeout = 60s
      |  graceful-shutdown-timeout = 20s
      |}""".stripMargin

  override protected def assertValid(config: HttpServerConfig): TestResult =
    assertTrue(
      config.host == "0.0.0.0",
      config.port == 8080,
      config.maxHeaderSize == ConfigMemorySize.ofBytes(16 * 1024),
      config.maxInitialLineLength == ConfigMemorySize.ofBytes(8 * 1024),
      config.keepAlive,
      config.idleTimeout == 60.second,
      config.gracefulShutdownTimeout == 20.seconds,
    )

  override protected val invalidCases: Map[String, String] = Map(
    "host is missing" ->
      """
        |http-server {
        |  port = 8080
        |  max-header-size = 16KiB
        |  max-initial-line-length = 8KiB
        |  keep-alive = true
        |  idle-timeout = 60s
        |  graceful-shutdown-timeout = 20s
        |}""".stripMargin,
    "port is not a number" ->
      """
        |http-server {
        |  host = "0.0.0.0"
        |  port = "abc"
        |  max-header-size = 16KiB
        |  max-initial-line-length = 8KiB
        |  keep-alive = true
        |  idle-timeout = 60s
        |  graceful-shutdown-timeout = 20s
        |}""".stripMargin,
    "max-header-size has unknown unit" ->
      """
        |http-server {
        |  host = "0.0.0.0"
        |  port = 8080
        |  max-header-size = 16XB
        |  max-initial-line-length = 8KiB
        |  keep-alive = true
        |  idle-timeout = 60s
        |  graceful-shutdown-timeout = 20s
        |}""".stripMargin,
    "idle-timeout is not a duration" ->
      """
        |http-server {
        |  host = "0.0.0.0"
        |  port = 8080
        |  max-header-size = 16KiB
        |  max-initial-line-length = 8KiB
        |  keep-alive = true
        |  idle-timeout = "abc"
        |  graceful-shutdown-timeout = 20s
        |}""".stripMargin,
  )
}
