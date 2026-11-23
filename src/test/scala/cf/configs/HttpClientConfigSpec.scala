package cf.configs

import scala.concurrent.duration.DurationInt

import zio.test.*

object HttpClientConfigSpec extends ConfigSpec[HttpClientConfig] {

  override val configPath: String = "cf-api.http-client"

  override val validHocon: String =
    """
      cf-api {
        http-client {
          request-timeout = 30 seconds
          connection-timeout = 10 seconds
          retry-base-delay = 1 second
          retry-max-delay = 15 seconds
          retry-max-attempts = 5
          circuit-breaker {
            type = failure-count
            reset-timeout = 30 seconds
            parameters {
              max-failures = 5
            }
          }
        }
      }
    """

  override def assertValid(config: HttpClientConfig): TestResult =
    assertTrue(
      config.requestTimeout == 30.seconds,
      config.connectionTimeout == 10.seconds,
      config.retryBaseDelay == 1.second,
      config.retryMaxDelay == 15.seconds,
      config.retryMaxAttempts == 5,
      config.circuitBreaker == CircuitBreakerConfig.FailureCount(5, 30.seconds),
    )

  override val invalidCases: Map[String, String] = Map(
    "request-timeout is malformed" -> """
      cf-api {
        http-client {
          request-timeout = "abc"
          connection-timeout = 10 seconds
          retry-base-delay = 1 second
          retry-max-delay = 15 seconds
          retry-max-attempts = 5
          circuit-breaker {
            type = failure-count
            reset-timeout = 30 seconds
            failure-count {
              max-failures = 5
            }
          }
        }
      }
    """,
    "circuit-breaker has unknown type" -> """
      cf-api {
        http-client {
          request-timeout = 30 seconds
          connection-timeout = 10 seconds
          retry-base-delay = 1 second
          retry-max-delay = 15 seconds
          retry-max-attempts = 5
          circuit-breaker {
            type = magic
            reset-timeout = 30 seconds
          }
        }
      }
    """,
  )
}
