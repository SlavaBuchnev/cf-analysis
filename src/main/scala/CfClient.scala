import zio.{Chunk, Task, ZIO, ZLayer}
import zio.http.{Client, QueryParams, Request, URL}
import zio.json.*

import java.security.MessageDigest

class CfClient(
    client: Client,
    cfgOpt: Option[CodeforcesAuthConfig],
    groupCode: Option[String],
) {

  private val baseUrl = URL.decode("https://codeforces.com/api").toOption.get

  private def generateApiSig(methodName: String, params: Map[String, String]): Option[String] =
    cfgOpt.map { cfg =>
      val rand = scala.util.Random.alphanumeric.take(6).mkString
      val sortedParams =
        (params + ("apiKey" -> cfg.apiKey)).toSeq.sortBy(_._1).map { case (k, v) => s"$k=$v" }.mkString("&")
      val stringToSign = s"$rand/$methodName?$sortedParams#${cfg.apiSecret}"
      val md = MessageDigest.getInstance("SHA-512")
      val hash = md.digest(stringToSign.getBytes("UTF-8")).map(b => f"${b & 0xff}%02x").mkString
      s"$rand$hash"
    }

  private def request(
      method: String,
      params: Map[String, String],
  ): Task[String] = {
    val time = System.currentTimeMillis() / 1000
    val baseParams = params + ("time" -> time.toString)
    val withKey = baseParams ++ cfgOpt.map("apiKey" -> _.apiKey)
    val sig = generateApiSig(method, baseParams)
    val finalParams = withKey ++ sig.map("apiSig" -> _)

    val queryParams = QueryParams(finalParams.toSeq.map { case (k, v) => (k, Chunk(v)) }*)
    val uri = (baseUrl / method).setQueryParams(queryParams)
    val req = Request.get(uri)

    ZIO.logInfo(s"GET $uri") *>
      client.batched(req).flatMap { resp =>
        resp.body.asString.flatMap { body =>
          if (body.trim.startsWith("<")) {
            ZIO.fail(
              new RuntimeException(
                s"Codeforces returned HTML (Cloudflare challenge?). First 200 chars: ${body.take(200)}",
              ),
            )
          } else ZIO.succeed(body)
        }
      }
  }

  def getStatus(
      contestId: Int,
      from: Option[Int] = None,
      count: Option[Int] = None,
  ): Task[List[CfSubmission]] = {
    val params = Map("contestId" -> contestId.toString, "asManager" -> "true") ++
      from.map("from" -> _.toString) ++
      count.map("count" -> _.toString) ++
      groupCode.map("groupCode" -> _)
    request("contest.status", params).flatMap { json =>
      json.fromJson[CfApiResponse] match {
        case Right(resp) if resp.status == "OK" => ZIO.succeed(resp.result.map(normalizeSubmission))
        case Right(resp) => ZIO.fail(new RuntimeException(s"API error: ${resp.comment.getOrElse("unknown")}"))
        case Left(err) => ZIO.logError(json) *> ZIO.fail(new RuntimeException(s"Parse error: $err"))
      }
    }
  }

  private def normalizeSubmission(s: CfSubmission): CfSubmission =
    s.copy(programmingLanguage = LanguageNormalizer.normalize(s.programmingLanguage))
}

object CfClient {
  val layer: ZLayer[Client & App, Nothing, CfClient] =
    ZLayer.fromFunction((client: Client, config: App) => new CfClient(client, config.codeforces, config.groupCode))
}
