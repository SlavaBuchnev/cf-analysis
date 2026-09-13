import zio.*
import zio.http.*

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

object Main extends ZIOAppDefault {

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    (for {
      config <- ZIO.succeed(App.load())

      _ <- ZIO.logInfo(
        if (config.handles.isEmpty) "Public contest mode: tracking all participants"
        else s"Tracking handles: ${config.handles.mkString(", ")}",
      )
      _ <- ZIO.logInfo(s"Group code: ${config.groupCode.getOrElse("(public)")}")

      registry = MetricsCollector.registry

      _ <- Server
        .serve(metricsRoutes(registry))
        .provide(Server.defaultWithPort(config.httpServer.port))
        .fork

      httpClient <- ZIO.service[Client]
      api = new CfClient(httpClient, config.codeforces, config.groupCode)
      service = new CodeforcesService(api)

      stateRefs <- Ref.make(Map.empty[Int, Long])

      _ <- ZIO.logInfo("Loading initial data for contests...")
      _ <- ZIO.foreach(config.contestIds) { cid =>
        for {
          _ <- ZIO.logInfo(s"Loading contest $cid")
          statuses <- service.loadFullContestData(cid)
          _ <- ZIO.succeed(
            MetricsCollector.updateContestMetrics(cid, statuses, config.handles, config.groupCode),
          )
          lastId = statuses.map(_.id).maxOption.getOrElse(0L)
          _ <- stateRefs.update(_ + (cid -> lastId))
        } yield ()
      }

      _ <- ZIO.foreach(config.contestIds) { cid =>
        updateLoop(service, cid, stateRefs, config)
          .repeat(Schedule.fixed(Duration.fromScala(config.pollInterval)))
          .forkDaemon
      }

      _ <- ZIO.never
    } yield ()).provide(Client.default)

  private def metricsRoutes(registry: PrometheusMeterRegistry): Routes[Any, Response] =
    Routes(
      Method.GET / "metrics" -> handler { (_: Request) =>
        Response.text(registry.scrape())
      },
    )

  private def updateLoop(
      service: CodeforcesService,
      contestId: Int,
      stateRefs: Ref[Map[Int, Long]],
      config: App,
  ): ZIO[Any, Throwable, Unit] =
    for {
      currentMap <- stateRefs.get
      lastId <- currentMap.get(contestId) match {
        case Some(id) => ZIO.succeed(id)
        case None => ZIO.fail(new RuntimeException(s"No state for contest $contestId"))
      }
      newStatuses <- service.fetchNewStatuses(contestId, lastId, config.statusCount)
      _ <-
        if (newStatuses.nonEmpty) {
          for {
            allStatuses <- service.loadFullContestData(contestId)
            _ <- ZIO.succeed(
              MetricsCollector.updateContestMetrics(contestId, allStatuses, config.handles, config.groupCode),
            )
            newMaxId = allStatuses.map(_.id).maxOption.getOrElse(0L)
            _ <- stateRefs.update(_ + (contestId -> newMaxId))
            _ <- ZIO.logInfo(s"Contest $contestId updated with ${newStatuses.size} new submissions")
          } yield ()
        } else ZIO.unit
    } yield ()
}
