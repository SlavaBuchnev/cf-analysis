package cf.utils

import zio.{Duration, ZIO, ZLayer}

import cf.configs.RateLimiterConfig

import nl.vroste.rezilience.RateLimiter as ZioRateLimiter

/** Настройка и создание rate limiter'а для ограничения запросов к Codeforces API.
  *
  * Создаёт [[ZioRateLimiter]] на основе [[RateLimiterConfig]], а также предоставляет `noop`-реализацию, пропускающую
  * все запросы без ограничений.
  */
object RateLimiter {
  val layer: ZLayer[RateLimiterConfig, Throwable, ZioRateLimiter] =
    ZLayer.scoped {
      for {
        cfg <- ZIO.service[RateLimiterConfig]
        rl <- ZioRateLimiter.make(cfg.max, Duration.fromScala(cfg.interval))
          <& ZIO.logInfo(s"Create Rate Limiter: max=${cfg.max} per ${cfg.interval}")
      } yield rl
    }

  val noop = new ZioRateLimiter {
    override def apply[R, E, A](task: ZIO[R, E, A]): ZIO[R, E, A] = task
  }
}
