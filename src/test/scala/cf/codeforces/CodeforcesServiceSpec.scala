package cf.codeforces

import scala.concurrent.duration.DurationInt

import zio.test.{Spec, TestEnvironment, TestResult, assertTrue}
import zio.{Scope, ULayer, ZIO, ZLayer}

import cf.configs.{CfAuthConfig, CircuitBreakerConfig, HttpClientConfig}
import cf.models.Fixtures.submission
import cf.utils.MockedSpec

object CodeforcesServiceSpec extends MockedSpec {

  def spec: Spec[TestEnvironment & Scope, Any] =
    suite("CodeforcesServiceSpec")(
      loadFullContestDataTests,
      fetchRecentStatusesTests,
    )

  /** Тестовая конфигурация с заданным лимитом пагинации. */
  private def authConfig(maxSubmissions: Int): CfAuthConfig =
    CfAuthConfig(
      key = "key",
      secret = "secret",
      logFullUri = false,
      maxSubmissions = maxSubmissions,
      httpClient = HttpClientConfig(
        requestTimeout = 30.seconds,
        connectionTimeout = 10.seconds,
        retryBaseDelay = 1.second,
        retryMaxDelay = 15.seconds,
        retryMaxAttempts = 5,
        circuitBreaker = CircuitBreakerConfig.FailureCount(5, 30.seconds),
      ),
    )

  private def authConfigLayer(maxSubmissions: Int): ULayer[CfAuthConfig] =
    ZLayer.succeed(authConfig(maxSubmissions))

  private def test(label: String, maxSubmissions: Int = Int.MaxValue)(
      f: (CodeforcesClient, CodeforcesService) => ZIO[Any, Throwable, TestResult],
  ): Spec[TestEnvironment & Scope, Any] =
    testWithMock[CodeforcesClient, CfAuthConfig, CodeforcesService](
      label,
      mock[CodeforcesClient],
      authConfigLayer(maxSubmissions),
      CodeforcesService.layer,
    )(f)

  private def loadFullContestDataTests: Spec[TestEnvironment & Scope, Any] =
    suite("loadFullContestData")(
      test("returns submissions from single page") { (client, service) =>
        client.getStatus
          .expects(1, Some(1), Some(1000))
          .returnsZIO(List(submission(id = 1L), submission(id = 2L)))
        client.getStatus
          .expects(1, Some(3), Some(1000))
          .returnsZIO(Nil)
        for {
          result <- service.loadFullContestData(1)
        } yield assertTrue(result.map(_.id) == List(1L, 2L))
      },
      test("concatenates multiple pages") { (client, service) =>
        client.getStatus
          .expects(1, Some(1), Some(1000))
          .returnsZIO(List(submission(id = 1L), submission(id = 2L)))
        client.getStatus
          .expects(1, Some(3), Some(1000))
          .returnsZIO(List(submission(id = 3L)))
        client.getStatus
          .expects(1, Some(4), Some(1000))
          .returnsZIO(Nil)
        for {
          result <- service.loadFullContestData(1)
        } yield assertTrue(result.map(_.id) == List(1L, 2L, 3L))
      },
      test("returns Nil when first page is empty") { (client, service) =>
        client.getStatus.expects(1, Some(1), Some(1000)).returnsZIO(Nil)
        for {
          result <- service.loadFullContestData(1)
        } yield assertTrue(result.isEmpty)
      },
      test("advances from by batch size") { (client, service) =>
        client.getStatus
          .expects(1, Some(1), Some(1000))
          .returnsZIO(List(submission(id = 1L), submission(id = 2L), submission(id = 3L)))
        client.getStatus
          .expects(1, Some(4), Some(1000))
          .returnsZIO(Nil)
        for {
          result <- service.loadFullContestData(1)
        } yield assertTrue(result.map(_.id) == List(1L, 2L, 3L))
      },
      test("propagates client error") { (client, service) =>
        client.getStatus
          .expects(1, Some(1), Some(1000))
          .returns(ZIO.fail(new RuntimeException("api down")))
        for {
          exit <- service.loadFullContestData(1).exit
        } yield assertTrue(
          exit.causeOption.exists(_.failures.exists(_.getMessage == "api down")),
        )
      },
      test("stops and returns partial data when API repeats submissions") { (client, service) =>
        val batch = (1 to 1000).map(i => submission(id = i.toLong)).toList
        client.getStatus.expects(1, Some(1), Some(1000)).returnsZIO(batch)
        client.getStatus.expects(1, Some(1001), Some(1000)).returnsZIO(batch)
        for {
          result <- service.loadFullContestData(1)
        } yield assertTrue(
          result.size == 1000,
          result.head.id == 1L,
          result.last.id == 1000L,
        )
      },
      test("stops and truncates when max submissions limit is reached", maxSubmissions = 3) { (client, service) =>
        client.getStatus
          .expects(1, Some(1), Some(1000))
          .returnsZIO((1 to 5).map(i => submission(id = i.toLong)).toList)
        for {
          result <- service.loadFullContestData(1)
        } yield assertTrue(result.map(_.id) == List(1L, 2L, 3L))
      },
    )

  private def fetchRecentStatusesTests: Spec[TestEnvironment & Scope, Any] =
    suite("fetchRecentStatuses")(
      test("requests with from=None and given count") { (client, service) =>
        client.getStatus
          .expects(1, None, Some(10))
          .returnsZIO(List(submission(id = 1L)))
        for {
          _ <- service.fetchRecentStatuses(1, 10)
        } yield assertTrue(true)
      },
      test("sorts submissions by id") { (client, service) =>
        client.getStatus
          .expects(1, None, Some(10))
          .returnsZIO(
            List(
              submission(id = 3L),
              submission(id = 1L),
              submission(id = 2L),
            ),
          )
        for {
          result <- service.fetchRecentStatuses(1, 10)
        } yield assertTrue(result.map(_.id) == List(1L, 2L, 3L))
      },
      test("returns Nil for empty response") { (client, service) =>
        client.getStatus.expects(1, None, Some(10)).returnsZIO(Nil)
        for {
          result <- service.fetchRecentStatuses(1, 10)
        } yield assertTrue(result.isEmpty)
      },
      test("propagates client error") { (client, service) =>
        client.getStatus
          .expects(1, None, Some(10))
          .returns(ZIO.fail(new RuntimeException("boom")))
        for {
          exit <- service.fetchRecentStatuses(1, 10).exit
        } yield assertTrue(
          exit.causeOption.exists(_.failures.exists(_.getMessage == "boom")),
        )
      },
    )
}
