package cf.configs

import scala.concurrent.duration.FiniteDuration

import pureconfig.ConfigReader

/** Конфигурация rate limiter'а: максимальное число запросов за интервал времени. */
case class RateLimiterConfig(
    max: Int,
    interval: FiniteDuration,
) derives ConfigReader
