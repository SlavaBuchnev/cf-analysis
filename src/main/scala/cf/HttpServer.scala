package cf

import zio.{Task, ZIO}
import zio.http.Server

import cf.metrics.{MetricsCollector, MetricsRoutes}

object HttpServer {
  def start(port: Int): Task[Unit] =
    Server
      .serve(MetricsRoutes.routes(MetricsCollector.registry))
      .provide(Server.defaultWithPort(port))
      .unit
}
