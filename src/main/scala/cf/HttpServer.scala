package cf

import zio.{Duration, ZIO, ZLayer}
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
