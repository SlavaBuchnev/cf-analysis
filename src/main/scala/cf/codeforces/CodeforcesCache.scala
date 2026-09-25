package cf.codeforces

import scala.concurrent.duration.FiniteDuration
import zio.{Clock, Duration, Ref, Task, UIO, ZIO, ZLayer}
import zio.cache.{Cache, Lookup}

import cf.configs.CacheConfig
import cf.metrics.MetricsCollector
import cf.models.{ContestEntry, ContestState}

class CodeforcesCache(
    service: CodeforcesService,
    cache: Cache[Int, Throwable, ContestEntry],
    lastAccess: Ref[Map[Int, Long]],
    config: CacheConfig,
) {

  private def toZioDurationMillis(duration: FiniteDuration): Long = Duration.fromScala(duration).toMillis

  private val refreshAfter = toZioDurationMillis(config.refreshAfter)
  private val idleTtl = toZioDurationMillis(config.idleTtl)

  private val recentCount: Int = config.recentCount
  private val warmupSet: Set[Int] = config.warmupContestIds.toSet

  private def nowMillis: UIO[Long] = Clock.instant.map(_.toEpochMilli)

  def get(contestId: Int): Task[ContestState] = for {
    now <- nowMillis
    entry <- cache.get(contestId)
    state <- entry.state.get
    _ <- lastAccess.update(_ + (contestId -> now))
    _ <- maybeRefresh(contestId, entry, now)
  } yield state

  def warmup: UIO[Unit] =
    ZIO.ifZIO(ZIO.succeed(warmupSet.isEmpty))(
      onTrue = ZIO.logInfo("Cache warmup disabled"),
      onFalse = ZIO.logInfo(s"Warming up cache with contests: ${warmupSet.mkString(", ")}") *>
        ZIO.foreachDiscard(warmupSet) { cid =>
          cache
            .get(cid)
            .tap(_ => ZIO.logInfo(s"Warmup of contest $cid complete"))
            .catchAllCause(e => ZIO.logErrorCause(s"Warmup of contest $cid failed", e))
        },
    )

  def evictIdle: UIO[Int] = for {
    now <- nowMillis
    access <- lastAccess.get
    idle = access.collect {
      case (cid, t) if now - t > idleTtl && !warmupSet.contains(cid) => cid
    }.toList
    _ <- ZIO.foreachDiscard(idle) { cid =>
      cache.invalidate(cid) *> lastAccess.update(_ - cid)
    }
    _ <- ZIO.when(idle.nonEmpty)(
      ZIO.logInfo(s"Evicted ${idle.size} idle contests: ${idle.mkString(", ")}"),
    )
  } yield idle.size

  private def maybeRefresh(
      contestId: Int,
      entry: ContestEntry,
      now: Long,
  ): UIO[Unit] =
    for {
      should <- entry.lastRefreshed.modify { last =>
        if (now - last > refreshAfter) (true, now) else (false, last)
      }
      _ <- ZIO.when(should) {
        (for {
          recent <- service.fetchRecentStatuses(contestId, recentCount)
          _ <- entry.state.update(_.merge(recent))
          _ <- entry.state.get.flatMap { s =>
            ZIO.succeed(MetricsCollector.updateContestMetrics(contestId, s.sorted))
          }
          _ <- ZIO.logDebug(s"Refreshed contest $contestId")
        } yield ()).catchAllCause { c =>
          ZIO.logErrorCause(s"Refresh contest $contestId failed", c) *>
            entry.lastRefreshed.set(0L)
        }
      }
    } yield ()

}

object CodeforcesCache {
  val layer: ZLayer[CodeforcesService & CacheConfig, Throwable, CodeforcesCache] =
    ZLayer.fromZIO {
      for {
        service <- ZIO.service[CodeforcesService]
        cfg <- ZIO.service[CacheConfig]
        lastAccess <- Ref.make(Map.empty[Int, Long])
        cache <- Cache.make[Int, Any, Throwable, ContestEntry](
          capacity = cfg.capacity,
          timeToLive = Duration.fromScala(cfg.ttl),
          lookup = Lookup(cid => loadEntry(service, cid)),
        )
      } yield new CodeforcesCache(service, cache, lastAccess, cfg)
    }

  private def loadEntry(service: CodeforcesService, cid: Int): ZIO[Any, Throwable, ContestEntry] =
    for {
      subs <- service.loadFullContestData(cid)
      initial = ContestState.from(subs)
      state <- Ref.make(initial)
      now <- ZIO.clockWith(_.instant.map(_.toEpochMilli))
      fresh <- Ref.make(now)
      _ <- ZIO.succeed(MetricsCollector.updateContestMetrics(cid, initial.sorted))
    } yield ContestEntry(state, fresh)
}
