import zio.{Duration, Schedule, ZIO, ZIOAppArgs, ZIOAppDefault}
import zio.http.Client

import cf.*
import cf.configs.{AppConfig, TrackingConfig}
import cf.utils.RateLimiter

object Main extends ZIOAppDefault {

  override def run: ZIO[ZIOAppArgs, Any, Any] =
    (for {
      config <- ZIO.service[TrackingConfig]
      worker <- ZIO.service[ContestUpdateWorker]

      _ <- worker.initialLoad
      _ <- ZIO.foreachDiscard(config.contestIds) { cid =>
        worker
          .run(cid)
          .repeat(Schedule.fixed(Duration.fromScala(config.pollInterval)))
          .fork
      }

      _ <- ZIO.never
    } yield ()).provide(
      // configs
      AppConfig.rateLimiterCfgLayer,
      AppConfig.cfApiCfgOptLayer,
      AppConfig.trackingCfgLayer,
      AppConfig.httpServerCfgLayer,
      // server
      Client.default,
      CfClient.layer,
      CfService.layer,
      HttpServer.layer,
      ContestUpdateWorker.layer,
      RateLimiter.layer,
    )
}
