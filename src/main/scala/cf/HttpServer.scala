package cf

import zio.{ZIO, ZLayer}
import zio.http.Server

import cf.configs.HttpServerConfig
import cf.metrics.{MetricsCollector, MetricsRoutes}

object HttpServer {
  val layer: ZLayer[HttpServerConfig, Throwable, Unit] =
    ZLayer.scoped {
      for {
        cfg <- ZIO.service[HttpServerConfig]
        _ <- Server
          .serve(MetricsRoutes.routes(MetricsCollector.registry))
          .provide(Server.defaultWithPort(cfg.port))
          .forkScoped
      } yield ()
    }
}
