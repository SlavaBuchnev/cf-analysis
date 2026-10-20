package cf.configs

import scala.concurrent.duration.FiniteDuration

import pureconfig.ConfigReader

/** Конфигурация кэша контестов: ёмкость, TTL, политика обновления и периодичность прогона reaper'а. */
final case class CacheConfig(
    capacity: Int,
    ttl: FiniteDuration,
    idleTtl: FiniteDuration,
    refreshAfter: FiniteDuration,
    recentCount: Int,
    reaperInterval: FiniteDuration,
    warmupContestIds: List[Int],
) derives ConfigReader
