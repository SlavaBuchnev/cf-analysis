package cf.codeforces

import zio.{Task, ZIO, ZLayer}

import cf.configs.CfAuthConfig
import cf.models.CfSubmission

/** Сервис доступа к данным контестов Codeforces.
  *
  * Предоставляет операции загрузки посылок контеста, скрывая детали пагинации и работы с [[CodeforcesClient]].
  * Пагинация защищена от аномалий API: если очередной батч не вносит новых посылок (API проигнорировал `from` и вернул
  * уже полученные), загрузка останавливается и возвращаются накопленные данные (с записью предупреждения в лог). Полная
  * загрузка дополнительно ограничена `cf-api.max-submissions`: при достижении лимита пагинация останавливается, в лог
  * пишется предупреждение и возвращаются накопленные посылки.
  */
trait CodeforcesService {

  /** Загрузить все посылки контеста (с пагинацией по страницам по 1000, не более `max-submissions` посылок). */
  def loadFullContestData(contestId: Int): Task[List[CfSubmission]]

  /** Получить последние `count` посылок контеста, отсортированные по id. */
  def fetchRecentStatuses(contestId: Int, count: Int): Task[List[CfSubmission]]
}

/** Реализация [[CodeforcesService]] поверх [[CodeforcesClient]]: пагинация страницами по 1000 с защитой от повторов и
  * лимитом полной загрузки (`cf-api.max-submissions`).
  */
class CodeforcesServiceImpl(api: CodeforcesClient, maxSubmissions: Int) extends CodeforcesService {

  private val pageSize = 1000

  override def loadFullContestData(contestId: Int): Task[List[CfSubmission]] =
    loadAllStatuses(contestId)

  private def loadAllStatuses(contestId: Int): Task[List[CfSubmission]] = {
    def loop(from: Int, acc: Vector[CfSubmission], prevBatchIds: Set[Long]): Task[List[CfSubmission]] =
      if (acc.size >= maxSubmissions)
        ZIO
          .logWarning(
            s"Pagination of contest $contestId reached the limit of $maxSubmissions submissions; " +
              s"returning ${acc.size} loaded submissions",
          )
          .as(acc.toList)
      else
        api.getStatus(contestId, Some(from), Some(pageSize)).flatMap { batch =>
          val nextFrom = from + batch.size
          val batchIds = batch.map(_.id).toSet
          if (batch.isEmpty) ZIO.succeed(acc.toList)
          else if (nextFrom <= from)
            ZIO.fail(new RuntimeException(s"Pagination is not advancing (from=$from, batchSize=${batch.size})"))
          else if (batchIds.subsetOf(prevBatchIds))
            ZIO
              .logWarning(
                s"Pagination of contest $contestId is repeating (from=$from): API returned already seen submissions; " +
                  s"returning ${acc.size} loaded submissions",
              )
              .as(acc.toList)
          else loop(nextFrom, (acc ++ batch).take(maxSubmissions), batchIds)
        }
    loop(1, Vector.empty, Set.empty)
  }

  override def fetchRecentStatuses(contestId: Int, count: Int): Task[List[CfSubmission]] =
    api.getStatus(contestId, None, Some(count)).map(_.sortBy(_.id))
}

/** Фабрика слоя [[CodeforcesService]]. */
object CodeforcesService {

  /** Слой сервиса поверх [[CodeforcesClient]] и [[cf.configs.CfAuthConfig]] (лимит пагинации `max-submissions`). */
  val layer: ZLayer[CodeforcesClient & CfAuthConfig, Nothing, CodeforcesService] =
    ZLayer.fromFunction((api: CodeforcesClient, cfg: CfAuthConfig) =>
      new CodeforcesServiceImpl(api, cfg.maxSubmissions),
    )
}
