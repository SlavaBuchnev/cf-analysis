package cf

import zio.{Duration, ZIO, ZLayer}

import cf.configs.RateLimiterConfig
import nl.vroste.rezilience.RateLimiter as ZioRateLimiter

object RateLimiter {
  val layer: ZLayer[RateLimiterConfig, Throwable, ZioRateLimiter] =
    ZLayer.scoped {
      for {
        cfg <- ZIO.service[RateLimiterConfig]
        rl <- ZioRateLimiter.make(cfg.max, Duration.fromScala(cfg.interval))
          <& ZIO.logInfo(s"Create Rate Limiter: max=${cfg.max} per ${cfg.interval}")
      } yield rl
    }
}
