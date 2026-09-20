package cf

import zio.{durationInt, ZIO, ZLayer}
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
                .maxHeaderSize(16 * 1024) // 16 KB
                .maxInitialLineLength(8 * 1024) // 8 KB
                .enableRequestStreaming
                .requestDecompression(true)
                .responseCompression()
                // --- Управление соединениями ---
                .keepAlive(true)
                .idleTimeout(60.seconds)
                .gracefulShutdownTimeout(20.seconds),
            ),
          )
          .forkScoped
      } yield ()
    }
}
