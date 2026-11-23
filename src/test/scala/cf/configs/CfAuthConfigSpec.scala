package cf.configs

import scala.concurrent.duration.DurationInt

import zio.test.*

object CfAuthConfigSpec extends ConfigSpec[CfAuthConfig] {

  override val configPath: String = "cf-api"

  override val validHocon: String =
    """
      |cf-api {
      |   key = "abc",
      |   secret = "def",
      |   log-full-uri = false
      |   http-client {
      |     request-timeout = 30 seconds
      |     connection-timeout = 10 seconds
      |     retry-base-delay = 1 second
      |     retry-max-delay = 15 seconds
      |     retry-max-attempts = 5
      |     circuit-breaker {
      |       type = failure-count
      |       reset-timeout = 30 seconds
      |       parameters {
      |         max-failures = 5
      |       }
      |     }
      |   }
      |}""".stripMargin

  override def assertValid(config: CfAuthConfig): TestResult =
    assertTrue(
      config.key == "abc",
      config.secret == "def",
      !config.logFullUri,
      config.httpClient.requestTimeout == 30.seconds,
      config.httpClient.retryMaxAttempts == 5,
      config.httpClient.circuitBreaker == CircuitBreakerConfig.FailureCount(5, 30.seconds),
    )

  override val invalidCases: Map[String, String] = Map(
    "secret is missing" -> """cf-api { key = "abc", log-full-uri = false }""",
    "key is missing" -> """cf-api { secret = "def", log-full-uri = false }""",
    "log-full-uri is missing" -> """cf-api { key = "abc", secret = "def" }""",
    "http-client is missing" -> """cf-api { key = "abc", secret = "def", log-full-uri = false }""",
  )

}
