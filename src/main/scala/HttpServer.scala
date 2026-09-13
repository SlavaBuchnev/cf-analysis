import zio.{Task, ZIO}
import zio.http.Server

object HttpServer {
  def start(port: Int): Task[Unit] =
    Server
      .serve(MetricsRoutes.routes(MetricsCollector.registry))
      .provide(Server.defaultWithPort(port))
      .unit
}
