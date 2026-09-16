package cf

import zio.{Ref, ZIO}

import cf.configs.AppConfig
import cf.metrics.MetricsCollector

object ContestUpdateWorker {

  /** Первичная загрузка всех контестов из конфига. */
  def initialLoad(
      service: CfService,
      config: AppConfig,
      stateRefs: Ref[Map[Int, Long]],
  ): ZIO[Any, Throwable, Unit] =
    for {
      _ <- ZIO.logInfo("Loading initial data for contests...")
      _ <- ZIO.foreachDiscard(config.contestIds) { cid =>
        for {
          _ <- ZIO.logInfo(s"Loading contest $cid")
          statuses <- service.loadFullContestData(cid)
          _ <- ZIO.succeed(
            MetricsCollector.updateContestMetrics(
              cid,
              statuses,
              config.handles,
              config.groupCode,
            ),
          )
          lastId = statuses.map(_.id).maxOption.getOrElse(0L)
          _ <- stateRefs.update(_ + (cid -> lastId))
        } yield ()
      }
    } yield ()

  /** Один цикл опроса: тянем новые посылки, если есть — пересчитываем метрики. */
  def run(
      service: CfService,
      contestId: Int,
      stateRefs: Ref[Map[Int, Long]],
      config: AppConfig,
  ): ZIO[Any, Throwable, Unit] =
    for {
      currentMap <- stateRefs.get
      lastId <- currentMap.get(contestId) match {
        case Some(id) => ZIO.succeed(id)
        case None => ZIO.fail(new RuntimeException(s"No state for contest $contestId"))
      }
      newStatuses <- service.fetchNewStatuses(contestId, lastId, config.statusCount)
      _ <- ZIO.when(newStatuses.nonEmpty) {
        for {
          allStatuses <- service.loadFullContestData(contestId)
          _ <- ZIO.succeed(
            MetricsCollector.updateContestMetrics(
              contestId,
              allStatuses,
              config.handles,
              config.groupCode,
            ),
          )
          newMaxId = allStatuses.map(_.id).maxOption.getOrElse(0L)
          _ <- stateRefs.update(_ + (contestId -> newMaxId))
          _ <- ZIO.logInfo(s"Contest $contestId updated with ${newStatuses.size} new submissions")
        } yield ()
      }
    } yield ()
}
