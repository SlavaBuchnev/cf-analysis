package cf.utils

import zio.{Scope, Tag, ULayer, ZIO, ZLayer}
import zio.test.{Spec, TestEnvironment, TestResult}

import org.scalamock.ziotest.ScalamockZIOSpec

trait MockedSpec extends ScalamockZIOSpec {

  /** @param label
    *   название теста
    * @param mockM
    *   слой с моком зависимости M (создаётся на стороне вызова, чтобы каждый тест получил свой мок)
    * @param layer
    *   слой, собирающий тестируемый сервис R из M
    * @param f
    *   тело теста: получает мок M и сервис R
    */
  protected def testWithMock[M: Tag, R: Tag](
      label: String,
      mockM: ULayer[M],
      layer: ZLayer[M, Throwable, R],
  )(
      f: (M, R) => ZIO[Any, Throwable, TestResult],
  ): Spec[TestEnvironment & Scope, Any] =
    test(label) {
      for {
        r <- ZIO.service[R]
        m <- ZIO.service[M]
        result <- f(m, r)
      } yield result
    }.provide(layer, mockM)
}
