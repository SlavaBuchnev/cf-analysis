package cf.configs

import zio.test.*
import zio.Scope

import pureconfig.{ConfigReader, ConfigSource}
import pureconfig.error.ConfigReaderFailures

trait ConfigSpec[A: ConfigReader] extends ZIOSpecDefault {

  def configPath: String
  def validHocon: String
  def assertValid(config: A): TestResult
  def invalidCases: Map[String, String]

  final def load(hocon: String): Either[ConfigReaderFailures, A] =
    ConfigSource.string(hocon).at(configPath).load[A]

  final def configTests: Spec[Any, Nothing] = {
    val validTest: Spec[Any, Nothing] =
      test("loads valid config") {
        load(validHocon) match {
          case Right(cfg) => assertValid(cfg)
          case Left(err) => assertTrue(false) ?? err.prettyPrint()
        }
      }

    val invalidTests: Seq[Spec[Any, Nothing]] =
      invalidCases.toSeq.map { case (name, hocon) =>
        test(s"fails to load: $name") {
          assertTrue(load(hocon).isLeft)
        }
      }

    suite(getClass.getSimpleName.stripSuffix("$"))(validTest +: invalidTests)
  }

  override def spec: Spec[TestEnvironment & Scope, Any] = configTests
}
