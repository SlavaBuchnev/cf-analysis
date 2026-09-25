package cf.codeforces

import zio.{Chunk, Task, ZIO, ZLayer}
import zio.http.{Client, QueryParams, Request, URL}
import zio.json.*

import java.security.MessageDigest

import cf.configs.CfAuthConfig
import cf.models.{CfApiResponse, CfSubmission}
import cf.utils.LanguageNormalizer
import nl.vroste.rezilience.RateLimiter as ZioRateLimiter

class CodeforcesClient(
    client: Client,
    cfgOpt: Option[CfAuthConfig],
    rateLimiter: ZioRateLimiter,
) {

  private val baseUrl = URL.decode("https://codeforces.com/api").toOption.get

  private def generateApiSig(methodName: String, params: Map[String, String]): Option[String] =
    cfgOpt.map { cfg =>
      val rand = scala.util.Random.alphanumeric.take(6).mkString
      val sortedParams =
        (params + ("apiKey" -> cfg.key)).toSeq.sortBy(_._1).map { case (k, v) => s"$k=$v" }.mkString("&")
      val stringToSign = s"$rand/$methodName?$sortedParams#${cfg.secret}"
      val md = MessageDigest.getInstance("SHA-512")
      val hash = md.digest(stringToSign.getBytes("UTF-8")).map(b => f"${b & 0xff}%02x").mkString
      s"$rand$hash"
    }

  private def request(
      method: String,
      params: Map[String, String],
  ): Task[String] =
    rateLimiter {
      ZIO.suspend {
        val time = System.currentTimeMillis() / 1000
        val baseParams = params + ("time" -> time.toString)
        val withKey = baseParams ++ cfgOpt.map("apiKey" -> _.key)
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
    }

  def getStatus(
      contestId: Int,
      from: Option[Int] = None,
      count: Option[Int] = None,
  ): Task[List[CfSubmission]] = {
    val params = Map("contestId" -> contestId.toString) ++
      from.map("from" -> _.toString) ++
      count.map("count" -> _.toString) ++
      cfgOpt.map(_ => "asManager" -> "true")
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

object CodeforcesClient {
  val layer: ZLayer[Client & Option[CfAuthConfig] & ZioRateLimiter, Nothing, CodeforcesClient] =
    ZLayer.fromFunction((client: Client, config: Option[CfAuthConfig], rateLimiter: ZioRateLimiter) =>
      new CodeforcesClient(client, config, rateLimiter),
    )
}
