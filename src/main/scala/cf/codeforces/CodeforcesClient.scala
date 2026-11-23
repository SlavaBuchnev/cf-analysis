package cf.codeforces

import java.security.MessageDigest

import sttp.client4.*
import sttp.client4.httpclient.zio.SttpClient
import sttp.model.{StatusCode, Uri}
import zio.json.*
import zio.{Duration, Scope, Task, UIO, ZIO, ZLayer}

import cf.configs.CfAuthConfig
import cf.models.{CfApiResponse, CfSubmission}
import cf.utils.LanguageNormalizer

import nl.vroste.rezilience.CircuitBreaker.CircuitBreakerCallError
import nl.vroste.rezilience.{CircuitBreaker, RateLimiter as ZioRateLimiter, Retry}

/** Транзиентный сбой запроса к Codeforces: сетевая ошибка, таймаут, HTTP 5xx или HTML-заглушка Cloudflare.
  *
  * Такие ошибки участвуют в retry и считаются неудачей для circuit breaker'а. Ошибки API («контест не найден»), parse
  * error и HTTP 4xx транзиентными не считаются — повторы не применяются.
  */
final case class TransientCfError(message: String, cause: Option[Throwable] = None)
    extends RuntimeException(message, cause.orNull)

/** Клиент для работы с API Codeforces поверх sttp (`java.net.http.HttpClient`).
  *
  * Подписывает запросы цифровой подписью API (`apiSig`) на основе [[cf.configs.CfAuthConfig]], добавляет `time` и
  * `asManager=true`. Каждый запрос обёрнут в цепочку устойчивости: rate limit → retry → circuit breaker (политики из
  * `cf-api.http-client`; ретраятся и размыкают цепь только транзиентные ошибки). В логи уходят только безопасные
  * параметры запроса — `apiKey`/`apiSig` маскируются (полный URI — только при явном флаге `log-full-uri`).
  */
class CodeforcesClient(
    backend: SttpClient,
    cfg: CfAuthConfig,
    rateLimiter: ZioRateLimiter,
    retry: Retry[Throwable],
    circuitBreaker: CircuitBreaker[Throwable],
) {

  private val baseUri = Uri.parse("https://codeforces.com/api").toOption.get
  private val loggable = Set("contestId", "from", "count")

  // def, а не val: конструктор не должен обращаться к параметрам — scalamock создаёт мок подклассом с null-полями
  private def httpCfg = cfg.httpClient

  /** Загрузить посылки контеста методом `contest.status`: страница от `from` размером `count` (по умолчанию — с первой
    * посылки), языки программирования нормализуются через `LanguageNormalizer`.
    */
  def getStatus(
      contestId: Int,
      from: Option[Int] = None,
      count: Option[Int] = None,
  ): Task[List[CfSubmission]] = {
    val params = Map(
      "contestId" -> contestId.toString,
      "asManager" -> "true",
    ) ++ from.map("from" -> _.toString) ++ count.map("count" -> _.toString)
    execute("contest.status", params).flatMap { json =>
      json.fromJson[CfApiResponse] match {
        case Right(resp) if resp.status == "OK" => ZIO.succeed(resp.result.map(normalizeSubmission))
        case Right(resp) => ZIO.fail(new RuntimeException(s"API error: ${resp.comment.getOrElse("unknown")}"))
        case Left(err) =>
          ZIO.logError(s"Parse error: $err; body preview: ${json.take(200)}") *>
            ZIO.fail(new RuntimeException(s"Parse error: $err"))
      }
    }
  }

  /** Цепочка устойчивости запроса: rate limit → retry (только транзиентные) → circuit breaker → send. */
  private def execute(
      method: String,
      params: Map[String, String],
  ): Task[String] =
    rateLimiter {
      retry {
        circuitBreaker {
          send(method, params)
        }.mapError(unwrapCircuitBreaker)
      }
    }

  /** Отправка одного запроса: подпись, read-таймаут, маскированное логирование, классификация ответа. */
  private def send(
      method: String,
      params: Map[String, String],
  ): Task[String] =
    ZIO.suspend {
      ZIO.clockWith(_.currentTime(java.util.concurrent.TimeUnit.SECONDS)).flatMap { time =>
        val baseParams = params + ("time" -> time.toString)
        val finalParams = baseParams ++ Map("apiSig" -> generateApiSig(method, baseParams), "apiKey" -> cfg.key)

        val uri = baseUri.addPath(method).withParams(finalParams)
        val req = basicRequest.get(uri).readTimeout(httpCfg.requestTimeout).response(asStringAlways)

        logRequest(method, uri, params) *>
          backend
            .send(req)
            .mapError(e => TransientCfError(s"HTTP request failed: ${e.getMessage}", Some(e)))
            .flatMap(response => handleResponse(method, response.code, response.body))
      }
    }

  private def handleResponse(method: String, code: StatusCode, body: String): Task[String] =
    if (code.isServerError)
      ZIO.logWarning(s"$method returned HTTP ${code.code}: ${body.take(200)}") *>
        ZIO.fail(TransientCfError(s"$method returned HTTP ${code.code}"))
    else if (body.trim.startsWith("<"))
      ZIO.logWarning(s"$method returned HTML (Cloudflare challenge?): ${body.take(200)}") *>
        ZIO.fail(TransientCfError(s"$method returned HTML instead of JSON (Cloudflare challenge?)"))
    else if (!code.isSuccess)
      ZIO.fail(new RuntimeException(s"$method returned HTTP ${code.code}: ${body.take(200)}"))
    else ZIO.succeed(body)

  private def unwrapCircuitBreaker(error: CircuitBreakerCallError[Throwable]): Throwable =
    error.fold(
      new RuntimeException("Circuit breaker is open: Codeforces API is unavailable"),
      identity,
    )

  private def logRequest(method: String, uri: Uri, params: Map[String, String]): UIO[Unit] =
    if (cfg.logFullUri)
      ZIO.logWarning(s"GET $uri — cf-api.log-full-uri = true: в лог уходит полный URI, включая apiKey/apiSig")
    else {
      val safeParams = params.filter { case (k, _) => loggable(k) }
      val query = safeParams.map { case (k, v) => s"$k=$v" }.mkString("&")
      ZIO.logInfo(s"GET ${baseUri.addPath(method)}${if (query.nonEmpty) s"?$query" else ""}")
    }

  private def generateApiSig(methodName: String, params: Map[String, String]): String = {
    val rand = scala.util.Random.alphanumeric.take(6).mkString
    val sortedParams =
      (params + ("apiKey" -> cfg.key)).toSeq.sortBy(_._1).map { case (k, v) => s"$k=$v" }.mkString("&")
    val stringToSign = s"$rand/$methodName?$sortedParams#${cfg.secret}"
    val md = MessageDigest.getInstance("SHA-512")
    val hash = md.digest(stringToSign.getBytes("UTF-8")).map(b => f"${b & 0xff}%02x").mkString
    s"$rand$hash"
  }

  private def normalizeSubmission(s: CfSubmission): CfSubmission =
    s.copy(programmingLanguage = LanguageNormalizer.normalize(s.programmingLanguage))
}

/** Фабрика слоя [[CodeforcesClient]]: собирает политики устойчивости из `cf-api.http-client`. */
object CodeforcesClient {

  /** Транзиентный сбой — неудача для circuit breaker'а; прочие ошибки не размыкают цепь. */
  private val isTransientFailure: PartialFunction[Throwable, Boolean] = { case _: TransientCfError => true }

  /** Слой клиента: rate limiter — из окружения, retry и circuit breaker — из `cf-api.http-client`. */
  val layer: ZLayer[SttpClient & CfAuthConfig & ZioRateLimiter, Nothing, CodeforcesClient] =
    ZLayer.scoped {
      for {
        backend <- ZIO.service[SttpClient]
        cfg <- ZIO.service[CfAuthConfig]
        rl <- ZIO.service[ZioRateLimiter]
        retry <- retryPolicy(cfg)
        cb <- cfg.httpClient.circuitBreaker.build[Throwable](isTransientFailure)
      } yield new CodeforcesClient(backend, cfg, rl, retry, cb)
    }

  /** Retry с экспоненциальным backoff; через `widen` ретраятся только `TransientCfError`. */
  private def retryPolicy(cfg: CfAuthConfig): ZIO[Scope, Nothing, Retry[Throwable]] =
    Retry
      .make(
        min = Duration.fromScala(cfg.httpClient.retryBaseDelay),
        max = Duration.fromScala(cfg.httpClient.retryMaxDelay),
        maxRetries = Some(cfg.httpClient.retryMaxAttempts),
      )
      .map(_.widen[Throwable] { case t: TransientCfError => t })
}
