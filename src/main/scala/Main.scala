import zio.{Duration, Ref, Schedule, ZIO, ZIOAppArgs, ZIOAppDefault}
import zio.http.Client

import cf.*
import cf.configs.AppConfig

object Main extends ZIOAppDefault {

  override def run: ZIO[ZIOAppArgs, Any, Any] =
    (for {
      app <- ZIO.service[AppConfig]
      service <- ZIO.service[CfService]
      stateRefs <- Ref.make(Map.empty[Int, Long])

      _ <- ZIO.logInfo(
        if (app.handles.isEmpty) "Public contest mode: tracking all participants"
        else s"Tracking handles: ${app.handles.mkString(", ")}",
      )
      _ <- ZIO.logInfo(s"Group code: ${app.groupCode.getOrElse("(public)")}")

      _ <- HttpServer.start(app.httpServer.port).fork

      _ <- ContestUpdateWorker.initialLoad(service, app, stateRefs)
      _ <- ZIO.foreachDiscard(app.contestIds) { cid =>
        ContestUpdateWorker
          .run(service, cid, stateRefs, app)
          .repeat(Schedule.fixed(Duration.fromScala(app.pollInterval)))
          .fork
      }

      _ <- ZIO.never
    } yield ()).provide(
      AppConfig.layer,
      Client.default,
      CfClient.layer,
      CfService.layer,
    )
}
