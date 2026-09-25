package cf.codeforces

import zio.{Task, ZIO, ZLayer}

import cf.models.CfSubmission

trait CodeforcesService {
  def loadFullContestData(contestId: Int): Task[List[CfSubmission]]
  def fetchRecentStatuses(contestId: Int, count: Int): Task[List[CfSubmission]]
}

class CodeforcesServiceImpl(api: CodeforcesClient) extends CodeforcesService {

  // Загружаем все посылки контеста с пагинацией
  override def loadFullContestData(contestId: Int): Task[List[CfSubmission]] =
    loadAllStatuses(contestId)

  private def loadAllStatuses(contestId: Int): Task[List[CfSubmission]] = {
    def loop(from: Int, acc: List[CfSubmission]): Task[List[CfSubmission]] =
      api.getStatus(contestId, Some(from), Some(1000)).flatMap { batch =>
        if (batch.isEmpty) ZIO.succeed(acc)
        else loop(from + batch.size, acc ++ batch)
      }
    loop(1, Nil)
  }

  override def fetchRecentStatuses(contestId: Int, count: Int): Task[List[CfSubmission]] =
    api.getStatus(contestId, None, Some(count)).map(_.sortBy(_.id))
}

object CodeforcesService {
  val layer: ZLayer[CodeforcesClient, Nothing, CodeforcesService] =
    ZLayer.fromFunction(new CodeforcesServiceImpl(_))
}
