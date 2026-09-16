package cf

import zio.{Task, ZIO, ZLayer}

import cf.models.CfSubmission

class CfService(api: CfClient) {

  // Загружаем все посылки контеста с пагинацией
  def loadFullContestData(contestId: Int): Task[List[CfSubmission]] =
    loadAllStatuses(contestId)

  private def loadAllStatuses(contestId: Int): Task[List[CfSubmission]] = {
    def loop(from: Int, acc: List[CfSubmission]): Task[List[CfSubmission]] =
      api.getStatus(contestId, Some(from), Some(1000)).flatMap { batch =>
        if (batch.isEmpty) ZIO.succeed(acc)
        else loop(from + batch.size, acc ++ batch)
      }
    loop(1, Nil)
  }

  // для триггера обновления метрик
  def fetchNewStatuses(contestId: Int, lastSeenId: Long, count: Int): Task[List[CfSubmission]] =
    api.getStatus(contestId, None, Some(count)).map { statuses =>
      statuses.filter(_.id > lastSeenId).sortBy(_.id)
    }
}

object CfService {
  val layer: ZLayer[CfClient, Nothing, CfService] =
    ZLayer.fromFunction(new CfService(_))
}
