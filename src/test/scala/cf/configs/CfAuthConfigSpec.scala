package cf.configs

import zio.test.*

object CfAuthConfigSpec extends ConfigSpec[CfAuthConfig] {

  override protected val configPath: String = "cf-api"

  override protected val validHocon: String =
    """
      |cf-api { 
      |   key = "abc",
      |   secret = "def"
      |}""".stripMargin

  override protected def assertValid(config: CfAuthConfig): TestResult =
    assertTrue(
      config.key == "abc",
      config.secret == "def",
    )

  override protected val invalidCases: Map[String, String] = Map(
    "secret is missing" -> """cf-api { key = "abc" }""",
    "key is missing" -> """cf-api { secret = "def" }""",
  )

}
