package cf.codeforces

import zio.{Scope, ZIO}
import zio.test.{assertTrue, Spec, TestEnvironment, TestResult}

import cf.models.Fixtures.submission
import cf.utils.MockedSpec

object CodeforcesServiceSpec extends MockedSpec {

  def spec: Spec[TestEnvironment & Scope, Any] =
    suite("CodeforcesServiceSpec")(
      loadFullContestDataTests,
      fetchRecentStatusesTests,
    )

  private def test(label: String)(
      f: (CodeforcesClient, CodeforcesService) => ZIO[Any, Throwable, TestResult],
  ): Spec[TestEnvironment & Scope, Any] =
    testWithMock[CodeforcesClient, CodeforcesService](
      label,
      mock[CodeforcesClient],
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
