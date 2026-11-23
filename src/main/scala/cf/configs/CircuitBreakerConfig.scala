package cf.configs

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

import zio.{Duration, Schedule, Scope, ZIO}

import nl.vroste.rezilience.{CircuitBreaker, TrippingStrategy}
import pureconfig.error.{CannotConvert, ConfigReaderFailures, ConvertFailure}
import pureconfig.{ConfigCursor, ConfigReader}

/** Конфигурация circuit breaker'а для запросов к Codeforces.
  *
  * Стратегия задаётся полем `type`, её параметры — во вложенном блоке `parameters` (содержимое блока зависит от типа):
  * {{{
  * circuit-breaker {
  *   type = failure-count
  *   reset-timeout = 30 seconds
  *   parameters {
  *     max-failures = 5
  *   }
  * }
  * }}}
  *   - `failure-count` — [[CircuitBreakerConfig.FailureCount]]: размыкается после заданного числа подряд идущих
  *     неудачных вызовов;
  *   - `failure-rate` — [[CircuitBreakerConfig.FailureRate]]: размыкается, когда доля неудач за окно `sample-duration`
  *     превышает `failure-rate-threshold` (при минимальном трафике `min-throughput`).
  *
  * Необязательные поля `failure-rate` имеют дефолты, совпадающие с дефолтами rezilience (`0.5`, `1 minute`, `10`);
  * границы значений проверяются на этапе загрузки конфигурации. Неудачными считаются только ошибки, удовлетворяющие
  * предикату в [[build]] (для клиента Codeforces — только транзиентные, см. `TransientCfError` в `cf.codeforces`).
  *
  * Ридер написан вручную: явная диспетчеризация по `type` с понятной ошибкой (в отличие от автоматической coproduct-
  * деривации pureconfig).
  */
sealed trait CircuitBreakerConfig {

  def resetTimeout: FiniteDuration

  /** Построить circuit breaker по конфигурации. Неудачными считаются только ошибки, удовлетворяющие `isFailure` (для
    * клиента Codeforces — только транзиентные, см. `TransientCfError` в `cf.codeforces`); прочие ошибки не размыкают
    * цепь и не участвуют в счётчиках. Восстановление — через `reset-timeout`.
    */
  final def build[E](isFailure: PartialFunction[E, Boolean]): ZIO[Scope, Nothing, CircuitBreaker[E]] = {
    val resetPolicy = Schedule.spaced(Duration.fromScala(resetTimeout))
    this match {
      case CircuitBreakerConfig.FailureCount(maxFailures, _) =>
        CircuitBreaker.withMaxFailures[E](
          maxFailures = maxFailures,
          resetPolicy = resetPolicy,
          isFailure = isFailure,
        )
      case CircuitBreakerConfig.FailureRate(threshold, sampleDuration, minThroughput, _) =>
        CircuitBreaker.make[E](
          trippingStrategy = TrippingStrategy.failureRate(
            failureRateThreshold = threshold,
            sampleDuration = Duration.fromScala(sampleDuration),
            minThroughput = minThroughput,
          ),
          resetPolicy = resetPolicy,
          isFailure = isFailure,
        )
    }
  }
}

/** Варианты стратегии размыкания цепи и ручной ридер pureconfig (см. [[CircuitBreakerConfig]]). */
object CircuitBreakerConfig {

  /** Размыкание после `maxFailures` подряд идущих неудачных вызовов. */
  final case class FailureCount(
      maxFailures: Int,
      resetTimeout: FiniteDuration,
  ) extends CircuitBreakerConfig

  /** Размыкание при доле неудач выше `failureRateThreshold` (0..1, не включая границы) в окне `sampleDuration`. */
  final case class FailureRate(
      failureRateThreshold: Double,
      sampleDuration: FiniteDuration,
      minThroughput: Int,
      resetTimeout: FiniteDuration,
  ) extends CircuitBreakerConfig

  given ConfigReader[CircuitBreakerConfig] = ConfigReader.fromCursor { cursor =>
    for {
      obj <- cursor.asObjectCursor
      tpe <- obj.atKey("type").flatMap(_.asString)
      resetTimeout <- obj.atKey("reset-timeout").flatMap(ConfigReader[FiniteDuration].from)
      parameters <- obj.atKey("parameters")
      cfg <- tpe.trim.toLowerCase match {
        case "failure-count" => failureCountFrom(parameters, resetTimeout)
        case "failure-rate" => failureRateFrom(parameters, resetTimeout)
        case other =>
          Left(invalid(cursor, s"unknown type '$other', expected 'failure-count' or 'failure-rate'"))
      }
    } yield cfg
  }

  final private case class FailureCountFields(
      maxFailures: Int,
  ) derives ConfigReader

  final private case class FailureRateFields(
      failureRateThreshold: Option[Double],
      sampleDuration: Option[FiniteDuration],
      minThroughput: Option[Int],
  ) derives ConfigReader

  private def failureCountFrom(
      parameters: ConfigCursor,
      resetTimeout: FiniteDuration,
  ): ConfigReader.Result[CircuitBreakerConfig] =
    ConfigReader[FailureCountFields]
      .from(parameters)
      .flatMap { f =>
        if (f.maxFailures <= 0) Left(invalid(parameters, s"max-failures must be positive, got ${f.maxFailures}"))
        else Right(FailureCount(f.maxFailures, resetTimeout))
      }

  private def failureRateFrom(
      parameters: ConfigCursor,
      resetTimeout: FiniteDuration,
  ): ConfigReader.Result[CircuitBreakerConfig] =
    ConfigReader[FailureRateFields]
      .from(parameters)
      .flatMap { f =>
        val threshold = f.failureRateThreshold.getOrElse(0.5)
        if (threshold <= 0.0 || threshold >= 1.0)
          Left(invalid(parameters, s"failure-rate-threshold must be between 0 and 1 (exclusive), got $threshold"))
        else
          Right(
            FailureRate(
              failureRateThreshold = threshold,
              sampleDuration = f.sampleDuration.getOrElse(1.minute),
              minThroughput = f.minThroughput.getOrElse(10),
              resetTimeout = resetTimeout,
            ),
          )
      }

  private def invalid(cursor: ConfigCursor, because: String): ConfigReaderFailures =
    ConfigReaderFailures(
      ConvertFailure(CannotConvert("circuit-breaker", "CircuitBreakerConfig", because), cursor.origin, cursor.path),
    )
}
