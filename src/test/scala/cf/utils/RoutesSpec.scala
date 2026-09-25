package cf.utils

import zio.*
import zio.http.*
import zio.test.*

import org.scalamock.ziotest.ScalamockZIOSpec

/** Базовый трейт для тестов HTTP-маршрутов.
  *
  * Параметризован типом окружения `R`, которое нужно тестируемым маршрутам. Дочерний спек задаёт:
  *   - `routesUnderTest` — сами маршруты (Routes[R, Response]);
  *   - `envLayer` — слой, предоставляющий окружение R (моки и/или реальные сервисы). Создаётся заново на каждый
  *     `testWithRoutes`, поэтому моки не разделяются между тестами.
  *
  * Каждый тест получает пару `(R, Routes[Any, Response])`:
  *   - `R` — чтобы настраивать ожидания на моках и читать реальные сервисы;
  *   - `Routes[Any, Response]` — чтобы отправлять запросы без окружения.
  */
trait RoutesSpec[R: Tag] extends ScalamockZIOSpec {

  /** Фабрика тестируемых маршрутов. */
  def routesUnderTest: Routes[R, Response]

  /** Слой, предоставляющий окружение `R`. */
  def envLayer: ULayer[R]

  def testWithRoutes(label: String)(
      f: (R, Routes[Any, Response]) => ZIO[Any, Throwable, TestResult],
  ): Spec[TestEnvironment & Scope, Any] =
    test(label) {
      for {
        env <- ZIO.environment[R]
        r <- ZIO.service[R]
        built = routesUnderTest.provideEnvironment(env)
        result <- f(r, built)
      } yield result
    }.provide(envLayer)

  def get(path: String): Request =
    Request.get(URL.decode(path).toOption.get)

  def post(path: String, body: Body = Body.empty): Request =
    Request.post(URL.decode(path).toOption.get, body)

  def put(path: String, body: Body = Body.empty): Request =
    Request.put(URL.decode(path).toOption.get, body)

  def delete(path: String): Request =
    Request.delete(URL.decode(path).toOption.get)

  def runRoute(
      routes: Routes[Any, Response],
      request: Request,
  ): ZIO[Any, Throwable, Response] = ZIO.scoped(routes.runZIO(request))

  def bodyOf(response: Response): ZIO[Any, Throwable, String] =
    response.body.asString.orDie
}
