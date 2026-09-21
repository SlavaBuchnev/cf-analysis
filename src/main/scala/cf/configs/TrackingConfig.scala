package cf.configs

import scala.concurrent.duration.FiniteDuration

import pureconfig.ConfigReader

case class TrackingConfig(
    contestIds: List[Int],
    handles: List[String],
    pollInterval: FiniteDuration,
    statusCount: Int,
) derives ConfigReader
