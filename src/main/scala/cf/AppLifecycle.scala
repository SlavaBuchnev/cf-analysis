package cf

import zio.{Duration, Schedule, Scope, ZIO, ZLayer}

import cf.codeforces.CodeforcesCache
import cf.configs.CacheConfig

final class AppLifecycle(cache: CodeforcesCache, cfg: CacheConfig) {

  val start: ZIO[Scope, Throwable, Nothing] =
    cache.warmup *>
      cache.evictIdle
        .repeat(Schedule.fixed(Duration.fromScala(cfg.reaperInterval)))
        .forkScoped *>
      ZIO.never
}

object AppLifecycle {
  val layer: ZLayer[CodeforcesCache & CacheConfig, Nothing, AppLifecycle] =
    ZLayer.fromFunction(new AppLifecycle(_, _))
}
