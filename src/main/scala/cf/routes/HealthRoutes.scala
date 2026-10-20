package cf.routes

import zio.http.*
import zio.ZIO

/** Роуты для проверки живости сервиса.
  *
  * Сейчас содержит один эндпоинт `GET /ping`, возвращающий `200 OK` без тела.
  */
object HealthRoutes {
  val routes: Routes[Any, Response] =
    Routes(
      Method.GET / "ping" ->
        handler { (_: Request) =>
          ZIO.succeed(Response.ok)
        },
    )
}
