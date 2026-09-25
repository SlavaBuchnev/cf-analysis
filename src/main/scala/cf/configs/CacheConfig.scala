package cf.configs

import scala.concurrent.duration.FiniteDuration

import pureconfig.ConfigReader

final case class CacheConfig(
    capacity: Int,
    ttl: FiniteDuration,
    idleTtl: FiniteDuration,
    refreshAfter: FiniteDuration,
    recentCount: Int,
    reaperInterval: FiniteDuration,
    warmupContestIds: List[Int],
) derives ConfigReader
