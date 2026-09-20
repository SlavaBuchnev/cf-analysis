package cf.configs

import zio.test.*

object TrackingConfigSpec extends ConfigSpec[TrackingConfig] {

  override protected val configPath: String = "tracking"

  override protected val validHocon: String =
    """
       tracking {
          contest-ids = [1, 2]
          handles = ["Alice", "Bob"]
          poll-interval = "30 seconds"
          status-count = 100
       }
    """.stripMargin

  override protected def assertValid(config: TrackingConfig): TestResult =
    assertTrue(
      config.contestIds == List(1, 2),
      config.handles == List("Alice", "Bob"),
      config.pollInterval.toSeconds == 30L,
      config.statusCount == 100,
    )

  override protected val invalidCases: Map[String, String] = Map(
    "contest-ids is missing" ->
      """
         tracking {
            handles = []
            poll-interval = "5 seconds"
            status-count = 10
         }
    """,
    "poll-interval is malformed" ->
      """
         tracking {
            contest-ids = [1]
            handles = []
            poll-interval = "not-a-duration"
            status-count = 10
         }
    """,
  )

}
