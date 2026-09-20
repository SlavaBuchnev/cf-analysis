package cf.configs

import zio.test.*

object HttpServerConfigSpec extends ConfigSpec[HttpServerConfig] {

  override protected val configPath: String = "http-server"

  override protected val validHocon: String =
    """http-server { host = "0.0.0.0", port = 8080 }"""

  override protected def assertValid(config: HttpServerConfig): TestResult =
    assertTrue(
      config.host == "0.0.0.0",
      config.port == 8080,
    )

  override protected val invalidCases: Map[String, String] = Map(
    "host is missing" -> """http-server { port = 8080 }""",
    "port is not a number" -> """http-server { host = "0.0.0.0", port = "abc" }""",
  )

}
