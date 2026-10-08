package cf.routes

import zio.http.*
import zio.ZIO

object HealthRoutes {
  val routes: Routes[Any, Response] =
    Routes(
      Method.GET / "ping" ->
        handler { (_: Request) =>
          ZIO.succeed(Response.ok)
        },
    )
}
