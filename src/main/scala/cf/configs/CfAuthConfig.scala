package cf.configs

import pureconfig.ConfigReader

/** Конфигурация аутентификации в API Codeforces (apiKey и secret).
  *
  * Используется [[cf.codeforces.CodeforcesClient]] для подписи запросов. Опциональна: при её отсутствии запросы идут
  * без цифровой подписи.
  */
case class CfAuthConfig(
    key: String,
    secret: String,
) derives ConfigReader
