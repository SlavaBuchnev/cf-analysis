/** Корневой пакет приложения cf-analysis — сервиса аналитики контестов Codeforces.
  *
  * Содержит точку входа (`Main`), жизненный цикл ([[cf.AppLifecycle]]) и обёртку HTTP-сервера ([[cf.HttpServer]]);
  * предметная область распределена по подпакетам: `codeforces` (клиент, сервис и кэш Codeforces), `configs`
  * (конфигурация), `models` (модели данных), `routes` (HTTP-роуты), `metrics` (метрики Prometheus), `utils`
  * (вспомогательные компоненты).
  */
package object cf
