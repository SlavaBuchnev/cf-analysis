package cf

import zio.{Duration, ZIO, ZLayer}
import zio.http.Server

import cf.configs.HttpServerConfig
import cf.routes.HttpRoutes

object HttpServer {
  val layer: ZLayer[HttpServerConfig & HttpRoutes, Throwable, Unit] =
    ZLayer.scoped {
      for {
        cfg <- ZIO.service[HttpServerConfig]
        routes <- ZIO.service[HttpRoutes]
        _ <- Server
          .serve(routes.routes)
          .provide(
            Server.defaultWith(
              _.binding(cfg.host, cfg.port)
                // --- Производительность и лимиты ---
                .maxHeaderSize(cfg.maxHeaderSize.toBytes.toInt)
                .maxInitialLineLength(cfg.maxInitialLineLength.toBytes.toInt)
                .enableRequestStreaming
                .requestDecompression(true)
                .responseCompression()
                // --- Управление соединениями ---
                .keepAlive(cfg.keepAlive)
                .idleTimeout(Duration.fromScala(cfg.idleTimeout))
                .gracefulShutdownTimeout(Duration.fromScala(cfg.gracefulShutdownTimeout)),
            ),
          )
          .forkScoped
      } yield ()
    }
}
