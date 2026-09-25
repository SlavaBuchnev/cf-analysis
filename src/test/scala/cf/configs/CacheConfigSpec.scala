package cf.configs

import scala.concurrent.duration._
import zio.test.assertTrue
import zio.test.TestResult

object CacheConfigSpec extends ConfigSpec[CacheConfig] {
  override val configPath: String = "cache"

  override val validHocon: String =
    """
      |cache {
      |  capacity        = 100
      |  ttl             = 30 minutes
      |  idle-ttl        = 10 minutes
      |  refresh-after   = 1 minute
      |  recent-count    = 25
      |  reaper-interval = 30 seconds
      |  warmup-contest-ids = [1, 2, 3]
      |}
      |""".stripMargin

  override def assertValid(config: CacheConfig): TestResult =
    assertTrue(
      config.capacity == 100,
      config.ttl == 30.minutes,
      config.idleTtl == 10.minutes,
      config.refreshAfter == 1.minute,
      config.recentCount == 25,
      config.reaperInterval == 30.seconds,
      config.warmupContestIds == List(1, 2, 3),
    )

  override val invalidCases: Map[String, String] = Map(
    "missing capacity" ->
      """
        |cache {
        |  ttl             = 30 minutes
        |  idle-ttl        = 10 minutes
        |  refresh-after   = 1 minute
        |  recent-count    = 25
        |  reaper-interval = 30 seconds
        |  warmup-contest-ids = [1, 2, 3]
        |}
        |""".stripMargin,
    "wrong type for capacity" ->
      """
        |cache {
        |  capacity        = "one hundred"
        |  ttl             = 30 minutes
        |  idle-ttl        = 10 minutes
        |  refresh-after   = 1 minute
        |  recent-count    = 25
        |  reaper-interval = 30 seconds
        |  warmup-contest-ids = [1, 2, 3]
        |}
        |""".stripMargin,
    "invalid duration format for ttl" ->
      """
        |cache {
        |  capacity        = 100
        |  ttl             = "abc"
        |  idle-ttl        = 10 minutes
        |  refresh-after   = 1 minute
        |  recent-count    = 25
        |  reaper-interval = 30 seconds
        |  warmup-contest-ids = [1, 2, 3]
        |}
        |""".stripMargin,
    "warmup-contest-ids with non-integer element" ->
      """
        |cache {
        |  capacity        = 100
        |  ttl             = 30 minutes
        |  idle-ttl        = 10 minutes
        |  refresh-after   = 1 minute
        |  recent-count    = 25
        |  reaper-interval = 30 seconds
        |  warmup-contest-ids = [1, "two", 3]
        |}
        |""".stripMargin,
    "empty block" ->
      """
        |cache {}
        |""".stripMargin,
  )

}
