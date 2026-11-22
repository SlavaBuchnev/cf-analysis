package cf.configs

import scala.concurrent.duration.DurationInt

import zio.test.*

object CorsConfigSpec extends ConfigSpec[CorsConfig] {

  override val configPath: String = "cors"

  override val validHocon: String =
    """
      cors {
        enabled = true
        allowed-origins = ["http://localhost:3000", "https://example.com"]
        allowed-methods = ["GET"]
        allowed-headers = ["Content-Type"]
        max-age = 30 minutes
      }
    """

  override def assertValid(config: CorsConfig): TestResult =
    assertTrue(
      config.enabled,
      config.allowedOrigins == List("http://localhost:3000", "https://example.com"),
      config.allowedMethods == List("GET"),
      config.allowedHeaders == List("Content-Type"),
      config.maxAge == 30.minutes,
    )

  override val invalidCases: Map[String, String] = Map(
    "enabled is missing" -> """
      cors {
        allowed-origins = []
        allowed-methods = ["GET"]
        allowed-headers = ["Content-Type"]
        max-age = 30 minutes
      }
    """,
    "max-age is not a duration" -> """
      cors {
        enabled = true
        allowed-origins = []
        allowed-methods = ["GET"]
        allowed-headers = ["Content-Type"]
        max-age = "abc"
      }
    """,
  )
}
