package cf

import zio.test.{assertTrue, ZIOSpecDefault}

import cf.configs.*
import pureconfig.ConfigSource

object ConfigsSpec extends ZIOSpecDefault {

  private def loadAppConfig(hocon: String): Either[?, AppConfig] =
    ConfigSource.string(hocon).at("app").load[AppConfig]

  private def loadCf(hocon: String): Either[?, CfAuthConfig] =
    ConfigSource.string(hocon).at("codeforces").load[CfAuthConfig]

  def spec = suite("ConfigsSpec")(
    suite("AppConfig")(
      test("loads full valid config") {
        val hocon =
          """
            app {
              contest-ids = [1, 2]
              handles = ["Alice", "Bob"]
              poll-interval = "30 seconds"
              status-count = 100
              http-server { host = "0.0.0.0", port = 8080 }
              group-code = "group"
              codeforces { api-key = "key", api-secret = "secret" }
            }
          """
        val result = loadAppConfig(hocon)
        assertTrue(
          result.isRight,
          result.toOption.exists(_.contestIds == List(1, 2)),
          result.toOption.exists(_.handles == List("Alice", "Bob")),
          result.toOption.exists(_.pollInterval.toSeconds == 30L),
          result.toOption.exists(_.statusCount == 100),
          result.toOption.exists(_.groupCode.contains("group")),
          result.toOption.exists(_.codeforces.exists(_.apiKey == "key")),
        )
      },
      test("loads config without optional parameters") {
        val hocon =
          """
            app {
              contest-ids = [1]
              handles = []
              poll-interval = "5 seconds"
              status-count = 10
              http-server { host = "localhost", port = 9000 }
            }
          """
        val result = loadAppConfig(hocon)
        assertTrue(
          result.isRight,
          result.toOption.exists(_.groupCode.isEmpty),
          result.toOption.exists(_.codeforces.isEmpty),
          result.toOption.exists(_.handles.isEmpty),
        )
      },
      test("fails when required field contestIds is missing") {
        val hocon =
          """
            app {
              handles = []
              poll-interval = "5 seconds"
              status-count = 10
              http-server { host = "localhost", port = 9000 }
            }
          """
        val result = loadAppConfig(hocon)
        assertTrue(result.isLeft)
      },
      test("fails when pollInterval is malformed") {
        val hocon =
          """
            app {
              contest-ids = [1]
              handles = []
              poll-interval = "not-a-duration"
              status-count = 10
              http-server { host = "localhost", port = 9000 }
            }
          """
        val result = loadAppConfig(hocon)
        assertTrue(result.isLeft)
      },
    ),
    suite("CodeforcesAuthConfig")(
      test("loads valid config") {
        val hocon = """codeforces { api-key = "abc", api-secret = "def" }"""
        val result = loadCf(hocon)
        assertTrue(
          result.isRight,
          result.toOption.exists(_.key == "abc"),
          result.toOption.exists(_.secret == "def"),
        )
      },
      test("fails when apiSecret is missing") {
        val hocon = """codeforces { api-key = "abc" }"""
        val result = loadCf(hocon)
        assertTrue(result.isLeft)
      },
      test("fails when apiKey is missing") {
        val hocon = """codeforces { api-secret = "def" }"""
        val result = loadCf(hocon)
        assertTrue(result.isLeft)
      },
    ),
  )
}
