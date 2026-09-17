package cf.configs

import pureconfig.ConfigReader

import scala.concurrent.duration.FiniteDuration

case class RateLimiterConfig(
    max: Int,
    interval: FiniteDuration,
) derives ConfigReader
