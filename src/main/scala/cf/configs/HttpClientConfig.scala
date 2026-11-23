package cf.configs

import scala.concurrent.duration.FiniteDuration

import pureconfig.ConfigReader

/** Конфигурация HTTP-клиента к Codeforces (секция `app.cf-api.http-client`).
  *
  * Используется [[cf.codeforces.CodeforcesClient]] (таймауты, retry, circuit breaker). Сборка самого бэкенда sttp
  * (connection timeout) — в `Main`.
  *
  * @param requestTimeout
  *   read-таймаут одного запроса (sttp `readTimeout`): при превышении запрос падает с транзиентной ошибкой и уходит в
  *   retry/circuit breaker;
  * @param connectionTimeout
  *   таймаут установки TCP-соединения бэкенда (sttp `BackendOptions`) — защита от «зависших» соединений на уровне ниже
  *   HTTP;
  * @param retryBaseDelay
  *   начальная задержка перед повтором неудачного запроса; дальше задержка растёт экспоненциально с коэффициентом 2
  *   (первый повтор — сразу, см. `Retry.make`);
  * @param retryMaxDelay
  *   потолок задержки между повторами: после достижения интервал не растёт;
  * @param retryMaxAttempts
  *   максимальное число повторов после неудачной попытки (итоговое число попыток = 1 + `retryMaxAttempts`);
  * @param circuitBreaker
  *   circuit breaker: при серии транзиентных сбоев размыкает цепь и быстро отдаёт ошибку, не нагружая упавший API.
  */
final case class HttpClientConfig(
    requestTimeout: FiniteDuration,
    connectionTimeout: FiniteDuration,
    retryBaseDelay: FiniteDuration,
    retryMaxDelay: FiniteDuration,
    retryMaxAttempts: Int,
    circuitBreaker: CircuitBreakerConfig,
) derives ConfigReader
