package cf.codeforces

import scala.concurrent.duration.*
import zio.*
import zio.test.*

import cf.configs.CacheConfig
import cf.models.Fixtures.submission
import cf.utils.MockedSpec

object CodeforcesCacheSpec extends MockedSpec {

  private val baseConfig: CacheConfig = CacheConfig(
    capacity = 10,
    ttl = 1.hour,
    idleTtl = 10.minutes,
    refreshAfter = 5.minutes,
    recentCount = 10,
    reaperInterval = 1.minute,
    warmupContestIds = List(1, 2),
  )

  private def cacheLayer(cfg: CacheConfig): ZLayer[CodeforcesService, Throwable, CodeforcesCache] =
    ZLayer.succeed(cfg) >>> CodeforcesCache.layer

  private def testWithBaseCfg(label: String)(
      f: (CodeforcesService, CodeforcesCache) => ZIO[Any, Throwable, TestResult],
  ): Spec[TestEnvironment & Scope, Any] =
    testWithMock[CodeforcesService, CodeforcesCache](
      label,
      mock[CodeforcesService],
      cacheLayer(baseConfig),
    )(f)

  private def testWithoutWarmup(label: String)(
      f: (CodeforcesService, CodeforcesCache) => ZIO[Any, Throwable, TestResult],
  ): Spec[TestEnvironment & Scope, Any] =
    testWithMock[CodeforcesService, CodeforcesCache](
      label,
      mock[CodeforcesService],
      cacheLayer(baseConfig.copy(warmupContestIds = Nil)),
    )(f)

  def spec: Spec[TestEnvironment & Scope, Any] = suite("CodeforcesCacheSpec")(
    testWithBaseCfg("get loads data and returns state") { (service, cache) =>
      service.loadFullContestData.expects(1).returnsZIO(List(submission(id = 1L)))
      for {
        state <- cache.get(1)
      } yield assertTrue(
        state.sorted.map(_.id) == List(1L),
      )
    },
    testWithBaseCfg("get does not reload data within ttl") { (service, cache) =>
      service.loadFullContestData.expects(1).returnsZIO(List(submission(id = 1L)))
      for {
        _ <- ZIO.foreach(1 to 3)(_ => cache.get(1))
      } yield assertTrue(true)
    },
    testWithBaseCfg("warmup loads all contests from warmupSet") { (service, cache) =>
      service.loadFullContestData.expects(1).returnsZIO(List(submission(id = 1L)))
      service.loadFullContestData.expects(2).returnsZIO(List(submission(id = 2L)))
      for {
        _ <- cache.warmup
      } yield assertTrue(true)
    },
    testWithoutWarmup("warmup does nothing with empty warmupSet") { (_, cache) =>
      for {
        _ <- cache.warmup
      } yield assertTrue(true)
    },
    testWithoutWarmup("evictIdle evicts entry after idleTtl") { (service, cache) =>
      service.loadFullContestData.expects(1).returnsZIO(List(submission(id = 1L)))
      for {
        _ <- cache.get(1)
        _ <- TestClock.adjust(11.minutes)
        evicted <- cache.evictIdle
      } yield assertTrue(evicted == 1)
    },
    testWithBaseCfg("evictIdle does not evict warmup contests") { (service, cache) =>
      service.loadFullContestData.expects(1).returnsZIO(List(submission(id = 1L)))
      for {
        _ <- cache.get(1)
        _ <- TestClock.adjust(11.minutes)
        evicted <- cache.evictIdle
      } yield assertTrue(evicted == 0)
    },
    testWithoutWarmup("evictIdle does not evict when idleTtl is not expired") { (service, cache) =>
      service.loadFullContestData.expects(1).returnsZIO(List(submission(id = 1L)))
      for {
        _ <- cache.get(1)
        _ <- TestClock.adjust(5.minutes)
        evicted <- cache.evictIdle
      } yield assertTrue(evicted == 0)
    },
    testWithoutWarmup("maybeRefresh updates state after refreshAfter") { (service, cache) =>
      service.loadFullContestData.expects(1).returnsZIO(List(submission(id = 1L)))
      service.fetchRecentStatuses.expects(1, 10).returnsZIO(List(submission(id = 2L)))
      for {
        s1 <- cache.get(1)
        _ <- TestClock.adjust(6.minutes)
        s2 <- cache.get(1)
        s3 <- cache.get(1)
      } yield assertTrue(
        s1.sorted.map(_.id) == List(1L),
        s2.sorted.map(_.id) == List(1L),
        s3.sorted.map(_.id) == List(1L, 2L),
      )
    },
    testWithoutWarmup("maybeRefresh is not called repeatedly within refreshAfter") { (service, cache) =>
      service.loadFullContestData.expects(1).returnsZIO(List(submission(id = 1L)))
      service.fetchRecentStatuses.expects(1, 10).returnsZIO(List(submission(id = 2L)))
      for {
        _ <- cache.get(1)
        _ <- TestClock.adjust(6.minutes)
        _ <- cache.get(1)
        _ <- cache.get(1)
      } yield assertTrue(true)
    },
    testWithoutWarmup("maybeRefresh is not called before refreshAfter") { (service, cache) =>
      service.loadFullContestData.expects(1).returnsZIO(List(submission(id = 1L)))
      for {
        _ <- cache.get(1)
        _ <- TestClock.adjust(3.minutes)
        _ <- cache.get(1)
      } yield assertTrue(true)
    },
    testWithoutWarmup("maybeRefresh resets lastRefreshed on error") { (service, cache) =>
      service.loadFullContestData.expects(1).returnsZIO(List(submission(id = 1L)))
      service.fetchRecentStatuses
        .expects(1, 10)
        .returning(ZIO.fail(new RuntimeException("boom")))
      service.fetchRecentStatuses
        .expects(1, 10)
        .returning(ZIO.fail(new RuntimeException("boom")))
      for {
        _ <- cache.get(1)
        _ <- TestClock.adjust(6.minutes)
        _ <- cache.get(1)
        _ <- cache.get(1)
      } yield assertTrue(true)
    },
    testWithoutWarmup("loadEntry fails when loadFullContestData fails") { (service, cache) =>
      service.loadFullContestData
        .expects(1)
        .returning(ZIO.fail(new RuntimeException("no data")))
      for {
        exit <- cache.get(1).exit
      } yield assertTrue(
        exit.causeOption.exists(_.failures.exists(_.getMessage == "no data")),
      )
    },
    testWithoutWarmup("get caches different contests independently") { (service, cache) =>
      service.loadFullContestData.expects(1).returnsZIO(List(submission(id = 1L)))
      service.loadFullContestData.expects(2).returnsZIO(List(submission(id = 2L)))
      for {
        s1 <- cache.get(1)
        s2 <- cache.get(2)
        s1again <- cache.get(1)
      } yield assertTrue(
        s1.sorted.map(_.id) == List(1L),
        s2.sorted.map(_.id) == List(2L),
        s1again.sorted.map(_.id) == List(1L),
      )
    },
  )
}
