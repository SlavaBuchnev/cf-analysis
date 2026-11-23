package cf.utils

import zio.{Duration, ZIO, ZLayer}

import cf.configs.RateLimiterConfig

import nl.vroste.rezilience.RateLimiter as ZioRateLimiter

/** Настройка и создание rate limiter'а для ограничения запросов к Codeforces API.
  *
  * Создаёт [[nl.vroste.rezilience.RateLimiter RateLimiter]] rezilience на основе [[cf.configs.RateLimiterConfig]], а
  * также предоставляет `noop`-реализацию, пропускающую все запросы без ограничений.
  */
object RateLimiter {

  /** Слой rate limiter'а из конфигурации: не более `max` запросов за `interval`. */
  val layer: ZLayer[RateLimiterConfig, Throwable, ZioRateLimiter] =
    ZLayer.scoped {
      for {
        cfg <- ZIO.service[RateLimiterConfig]
        rl <- ZioRateLimiter.make(cfg.max, Duration.fromScala(cfg.interval))
          <& ZIO.logInfo(s"Create Rate Limiter: max=${cfg.max} per ${cfg.interval}")
      } yield rl
    }

  /** Заглушка, пропускающая запросы без ограничений (используется в тестах). */
  val noop = new ZioRateLimiter {
    override def apply[R, E, A](task: ZIO[R, E, A]): ZIO[R, E, A] = task
  }
}
