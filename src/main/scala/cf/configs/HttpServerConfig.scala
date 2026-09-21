package cf.configs

import scala.concurrent.duration.FiniteDuration

import com.typesafe.config.ConfigMemorySize
import pureconfig.ConfigReader

final case class HttpServerConfig(
    host: String,
    port: Int,
    maxHeaderSize: ConfigMemorySize,
    maxInitialLineLength: ConfigMemorySize,
    keepAlive: Boolean,
    idleTimeout: FiniteDuration,
    gracefulShutdownTimeout: FiniteDuration,
) derives ConfigReader
