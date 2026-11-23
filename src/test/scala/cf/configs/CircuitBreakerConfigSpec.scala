package cf.configs

import scala.concurrent.duration.DurationInt

import zio.Scope
import zio.test.*

object CircuitBreakerConfigSpec extends ConfigSpec[CircuitBreakerConfig] {

  override val configPath: String = "circuit-breaker"

  override val validHocon: String =
    """
      circuit-breaker {
        type = failure-count
        reset-timeout = 30 seconds
        parameters {
          max-failures = 5
        }
      }
    """

  override def assertValid(config: CircuitBreakerConfig): TestResult =
    assertTrue(
      config == CircuitBreakerConfig.FailureCount(5, 30.seconds),
    )

  override val invalidCases: Map[String, String] = Map(
    "unknown type" -> """
      circuit-breaker {
        type = magic
        reset-timeout = 30 seconds
        parameters {
          max-failures = 5
        }
      }
    """,
    "type is missing" -> """
      circuit-breaker {
        reset-timeout = 30 seconds
        parameters {
          max-failures = 5
        }
      }
    """,
    "reset-timeout is missing" -> """
      circuit-breaker {
        type = failure-count
        parameters {
          max-failures = 5
        }
      }
    """,
    "parameters block is missing" -> """
      circuit-breaker {
        type = failure-count
        reset-timeout = 30 seconds
      }
    """,
    "max-failures is not positive" -> """
      circuit-breaker {
        type = failure-count
        reset-timeout = 30 seconds
        parameters {
          max-failures = 0
        }
      }
    """,
    "failure-rate: threshold is out of bounds" -> """
      circuit-breaker {
        type = failure-rate
        reset-timeout = 30 seconds
        parameters {
          failure-rate-threshold = 1.5
        }
      }
    """,
  )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("CircuitBreakerConfigSpec")(
      configTests,
      test("loads failure-rate config with defaults for optional fields") {
        load("""
          circuit-breaker {
            type = failure-rate
            reset-timeout = 30 seconds
            parameters {
              failure-rate-threshold = 0.7
            }
          }
        """) match {
          case Right(cfg) =>
            assertTrue(cfg == CircuitBreakerConfig.FailureRate(0.7, 1.minute, 10, 30.seconds))
          case Left(err) => assertTrue(false) ?? err.prettyPrint()
        }
      },
      test("loads failure-rate config with all fields set") {
        load("""
          circuit-breaker {
            type = failure-rate
            reset-timeout = 30 seconds
            parameters {
              failure-rate-threshold = 0.5
              sample-duration = 2 minutes
              min-throughput = 20
            }
          }
        """) match {
          case Right(cfg) =>
            assertTrue(cfg == CircuitBreakerConfig.FailureRate(0.5, 2.minutes, 20, 30.seconds))
          case Left(err) => assertTrue(false) ?? err.prettyPrint()
        }
      },
    )
}
