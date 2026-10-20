package cf.configs

import pureconfig.ConfigReader

import scala.concurrent.duration.FiniteDuration

/** Конфигурация rate limiter'а: максимальное число запросов за интервал времени. */
case class RateLimiterConfig(
    max: Int,
    interval: FiniteDuration,
) derives ConfigReader
