package cf.utils

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import zio.{Duration, Scope, ZIO, ZLayer}
import zio.test.*

import cf.configs.RateLimiterConfig
import nl.vroste.rezilience.RateLimiter as ZioRateLimiter

object RateLimiterSpec extends ZIOSpecDefault {
  private def limiterLayer(
      max: Int,
      interval: FiniteDuration,
  ): ZLayer[Any, Throwable, ZioRateLimiter] =
    ZLayer.succeed(RateLimiterConfig(max, interval)) >>> RateLimiter.layer

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("RateLimiterSpec")(
      test("layer creates a RateLimiter and allows a call") {
        for {
          rl <- ZIO.service[ZioRateLimiter]
          r <- rl(ZIO.succeed(42))
        } yield assertTrue(r == 42)
      }.provide(limiterLayer(max = 1, interval = 2.seconds)),
      test("allows up to max calls without delay") {
        for {
          rl <- ZIO.service[ZioRateLimiter]
          _ <- ZIO.foreachDiscard(1 to 3)(_ => rl(ZIO.unit))
        } yield assertCompletes
      }.provide(limiterLayer(max = 3, interval = 2.seconds)),
      test("blocks the (max+1)-th call until the interval elapses") {
        for {
          rl <- ZIO.service[ZioRateLimiter]
          _ <- rl(ZIO.unit)
          fib <- rl(ZIO.succeed("ok")).fork

          _ <- TestClock.adjust(Duration.fromScala(1.seconds))
          status <- fib.status
          blocked = !status.isDone
          _ <- TestClock.adjust(Duration.fromScala(1.seconds))
          exit <- fib.await
        } yield assertTrue(blocked, exit.isSuccess)
      }.provide(limiterLayer(max = 1, interval = 2.seconds)),
      test("unblocks after the interval") {
        for {
          rl <- ZIO.service[ZioRateLimiter]
          _ <- rl(ZIO.unit)
          _ <- TestClock.adjust(Duration.fromScala(1.seconds))
          r <- rl(ZIO.succeed("second"))
        } yield assertTrue(r == "second")
      }.provide(limiterLayer(max = 1, interval = 1.second)),
    )
}
