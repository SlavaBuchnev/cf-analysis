/** Доступ к данным Codeforces.
  *
  * [[cf.codeforces.CodeforcesClient]] — HTTP-клиент к `codeforces.com/api` с цифровой подписью запросов и политиками
  * устойчивости (rate limit, retry, circuit breaker), [[cf.codeforces.CodeforcesService]] — бизнес-операции над
  * посылками (пагинация, защита от аномалий API), [[cf.codeforces.CodeforcesCache]] — кэш состояний контестов с
  * инкрементальным обновлением и вытеснением неактивных.
  */
package object codeforces
