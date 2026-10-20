package cf.routes

import cf.codeforces.CodeforcesCache
import zio.http.*
import zio.json.*
import zio.ZIO

/** Роуты для работы с данными контестов Codeforces.
  *
  * Эндпоинт `GET /api/contests/{id}` возвращает отсортированный список посылок
  * контеста из кэша в формате JSON. При ошибке доступа к кэшу возвращает `500`.
  */
object ContestsRoutes {

  val routes: Routes[CodeforcesCache, Response] =
    Routes(
      Method.GET / "api" / "contests" / int("id") ->
        handler { (id: Int, _: Request) =>
          ZIO
            .serviceWithZIO[CodeforcesCache](_.get(id))
            .foldZIO(
              e => ZIO.succeed(Response.status(Status.InternalServerError)),
              state => ZIO.succeed(Response.json(state.sorted.toJson)),
            )
        },
    )
}
