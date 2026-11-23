package cf.configs

import pureconfig.ConfigReader

/** Конфигурация доступа к API Codeforces (секция `app.cf-api`).
  *
  * Содержит аутентификацию (`apiKey`/`secret`) и настройки HTTP-клиента ([[HttpClientConfig]]): таймауты, retry и
  * circuit breaker. Значения `key`/`secret` подставляются из переменных окружения `KEY`/`SECRET` — без них приложение
  * не стартует, а запросы всегда идут с цифровой подписью. Флаг [[logFullUri]] включает логирование полного URI запроса
  * (включая `apiKey`/`apiSig`) — только для локальной отладки. Поле [[maxSubmissions]] ограничивает полную загрузку
  * контеста: при достижении лимита пагинация останавливается и возвращаются накопленные посылки (см.
  * [[cf.codeforces.CodeforcesService]]).
  *
  * Используется [[cf.codeforces.CodeforcesClient]] для подписи запросов и построения политик устойчивости.
  */
case class CfAuthConfig(
    key: String,
    secret: String,
    logFullUri: Boolean,
    maxSubmissions: Int,
    httpClient: HttpClientConfig,
) derives ConfigReader
