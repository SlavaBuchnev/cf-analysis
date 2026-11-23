/** HTTP-слой.
  *
  * [[cf.routes.ContestsRoutes]] — JSON-API контестов, [[cf.routes.MetricsRoutes]] — scrape метрик Prometheus,
  * [[cf.routes.HealthRoutes]] — healthcheck, [[cf.routes.StaticRoutes]] — отдача статического SPA из classpath; всё
  * агрегируется в [[cf.routes.HttpRoutes]].
  */
package object routes
