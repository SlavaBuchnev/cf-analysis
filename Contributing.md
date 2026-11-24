# Разработка

![Pipeline](https://github.com/SlavaBuchnev/cf-analysis/actions/workflows/pipeline.yml/badge.svg)
![Cron job status](https://api.cron-job.org/jobs/8838436/a85759c3aceb5a69/status-1.svg)

Бэкенд: Scala 3 / ZIO 2 (zio-http, zio-cache, zio-json), sttp client4, rezilience, Micrometer/Prometheus.
Фронтенд — статический SPA в `src/main/resources/static` (vanilla JS + Chart.js, без сборщика).

## Требования

- JDK **25** (eclipse-temurin)
- sbt **2.x**

## Окружение

Для запуска нужны переменные окружения `HTTP`, `PORT`, `KEY`, `SECRET` — они подставляются в
`application.conf` (`http-server.host/port`, `cf-api.key/secret`). Значения обычно берутся из `.env`
в корне репозитория (в `.gitignore`; IDEA подхватывает его в run-конфигурации `.run/runMain.run.xml`).
Без этих переменных приложение не стартует.

## Команды

```bash
sbt compile                 # компиляция
sbt lint                    # проверка форматирования (scalafmtCheckAll), тот же шаг гейтом в CI
sbt test                    # тесты (инкрементальный testQuick в sbt 2.x)
sbt "Test / testFull"       # полный прогон тестов вручную
sbt run                     # запуск Main
sbt doc                     # Scaladoc (настройки в project/ScaladocSettings.scala)

sbt Docker/publishLocal     # собрать Docker-образ локально
```

Перед коммитом прогоняйте `sbt scalafmtAll` (в CI форматирование проверяется до тестов).

## Тесты и моки

- zio-test + scalamock-zio; спеки — `object ...Spec extends ZIOSpecDefault` (или `MockedSpec`/`RoutesSpec`/`ConfigSpec`).
- Внешний API Codeforces в тестах не дёргается — мокаются `CodeforcesClient`/`CodeforcesService`.
- Моки создаются заново на каждый тест; временная логика — через `TestClock.adjust`.

## Конвенции

- Комментарии и Scaladoc — на русском, короткий Scaladoc у каждого класса/объекта.
- Форматирование — scalafmt 3.8.6 (`.scalafmt.conf`): `maxColumn = 120`, trailing commas, сортировка импортов.
- Неиспользуемые импорты и неполный `match` — ошибки компиляции.
- Зависимости между компонентами — через `ZLayer`, фабрики слоёв в компаньонах.

Устройство кода, конфигурация, CI/CD и подводные камни — в [AGENTS.md](AGENTS.md).

## Ветки и CI

- Основная ветка — `master`; фичи — `feat/*`, фиксы — `bug/*`.
- `.github/workflows/pipeline.yml` (на пуш в любую ветку): `testing` → `documentation` (Scaladoc на GitHub Pages,
  только master) и `publish` (образы в Docker Hub и GitHub Packages — см. [Readme.md](Readme.md)).
- Деплой на Render — `.github/workflows/deploy.yml`, запуск вручную.
