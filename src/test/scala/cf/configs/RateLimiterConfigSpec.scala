package cf.configs

import zio.test.*

object RateLimiterConfigSpec extends ConfigSpec[RateLimiterConfig] {

  override val configPath: String = "rate-limiter"

  override val validHocon: String =
    """
      rate-limiter {
        max = 10
        interval = "1 second"
      }
    """

  override def assertValid(config: RateLimiterConfig): TestResult =
    assertTrue(
      config.max == 10,
      config.interval.toSeconds == 1L,
    )

  override val invalidCases: Map[String, String] = Map(
    "max is missing" -> """
      rate-limiter {
        interval = "1 second"
      }
    """,
    "interval is malformed" -> """
      rate-limiter {
        max = 10
        interval = "not-a-duration"
      }
    """,
  )

}
