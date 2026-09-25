package cf.routes

import zio.{Scope, ULayer, ZIO}
import zio.http.{Response, Routes, Status}
import zio.test.{assertTrue, Spec, TestEnvironment}

import cf.codeforces.CodeforcesCache
import cf.models.ContestState
import cf.models.Fixtures.submission
import cf.utils.RoutesSpec

object ContestsRoutesSpec extends RoutesSpec[CodeforcesCache] {

  def envLayer: ULayer[CodeforcesCache] = mock[CodeforcesCache]

  def routesUnderTest: Routes[CodeforcesCache, Response] = ContestsRoutes.routes

  def spec: Spec[TestEnvironment & Scope, Any] =
    suite("ContestsRoutesSpec")(
      getContestTests,
      unknownRouteTests,
    )

  private def getContestTests = suite("GET /api/contests/:id")(
    testWithRoutes("returns 200 with sorted submissions") { (cache, routes) =>
      cache.get
        .expects(1)
        .returnsZIO(ContestState.from(List(submission(id = 2L), submission(id = 1L))))
      for {
        response <- runRoute(routes, get("/api/contests/1"))
        body <- bodyOf(response)
      } yield assertTrue(
        response.status == Status.Ok,
        body.contains("\"id\":1"),
        body.contains("\"id\":2"),
      )
    },
    testWithRoutes("returns empty array for contest with no submissions") { (cache, routes) =>
      cache.get.expects(1).returnsZIO(ContestState.from(Nil))
      for {
        response <- runRoute(routes, get("/api/contests/1"))
        body <- bodyOf(response)
      } yield assertTrue(
        response.status == Status.Ok,
        body == "[]",
      )
    },
    testWithRoutes("returns 500 when cache lookup fails") { (cache, routes) =>
      cache.get.expects(1).returns(ZIO.fail(new RuntimeException("boom")))
      for {
        response <- runRoute(routes, get("/api/contests/1"))
      } yield assertTrue(response.status == Status.InternalServerError)
    },
  )

  private def unknownRouteTests = suite("unknown routes")(
    testWithRoutes("returns 404 for unknown path") { (_, routes) =>
      for {
        response <- runRoute(routes, get("/api/unknown"))
      } yield assertTrue(response.status == Status.NotFound)
    },
  )
}
