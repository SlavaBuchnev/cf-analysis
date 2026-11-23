# cf-analysis

Сервис аналитики контестов Codeforces. Бэкенд на Scala 3 / ZIO 2 загружает посылки контеста через
публичное API Codeforces, кэширует их с инкрементальным обновлением и отдаёт: JSON-API для
встроенного фронта (`/api/contests/{id}`), метрики Prometheus (`/metrics`) и статический SPA
(`/`, vanilla JS + Chart.js из classpath). Упаковывается в Docker-образ (sbt-native-packager),
образ публикуется в Docker Hub и GitHub Packages, деплой — на Render через GitHub Actions (workflow ручного запуска).

## Технологический стек

- Scala **3.9.0**, sbt **2.0.8**, JDK **25** (eclipse-temurin; образ приложения и CI на 25-й)
- **ZIO 2.1.26**: zio-http 3.11.4 (сервер), zio-cache 0.3.0, zio-json (`derives JsonDecoder/JsonEncoder`)
- **sttp client4** 4.1.3 (модуль `zio`, бэкенд `java.net.http.HttpClient`) — исходящие HTTP-запросы к Codeforces
- **pureconfig-core** 0.17.10 — загрузка HOCON-конфигурации (секция `app` в `application.conf`)
- **rezilience** 0.10.5 — rate limiter для запросов к Codeforces
- **Micrometer** 1.17.1 + `micrometer-registry-prometheus` — метрики, endpoint `/metrics`
- logback-classic 1.6.3 — логирование (конфиг `src/main/resources/logback.xml`)
- Тесты: **zio-test** + **scalamock-zio** 7.6.0
- Плагины sbt: `sbt-native-packager` 1.12.0 (JavaAppPackaging + DockerPlugin) и `sbt-scalafmt` 2.6.1
  (алиас `lint` = `scalafmtCheckAll`, та же проверка гейтом в CI до тестов)
- Фронтенд: статика в `src/main/resources/static` (без сборщика, Chart.js UMD лежит в репозитории)

## Структура кода

Точка входа — `src/main/scala/Main.scala`: `ZIOAppDefault`, всё окружение собирается вручную через
`ZLayer` в `.provide(...)`. Пакеты под `cf`:

- `cf.AppLifecycle` — прогрев кэша при старте + фоновый reaper, вытесняющий неактивные контесты
  (`Schedule.fixed(reaperInterval)`, `forkScoped`), затем `ZIO.never`
- `cf.HttpServer` — обёртка над `zio-http Server` (лимиты заголовков, keep-alive, таймауты,
  graceful shutdown) на основе `HttpServerConfig`
- `cf.codeforces`:
  - `CodeforcesClient` — HTTP-клиент к `codeforces.com/api` поверх sttp (`java.net.http.HttpClient`):
    подпись запросов SHA-512 (`apiSig`), `time`-параметр, `asManager=true`, rate limit, детект
    HTML-ответа (Cloudflare-челлендж) по первому символу `<`. Каждый запрос обёрнут в цепочку
    устойчивости (read-таймаут sttp, retry с backoff и circuit breaker через rezilience;
    ретраятся только транзиентные ошибки — `TransientCfError`), в логи уходят только
    безопасные параметры (`apiKey`/`apiSig` маскируются, полный URI — по флагу `log-full-uri`).
    Политики строятся из `cf-api.http-client` (`CircuitBreakerConfig.build`)
  - `CodeforcesService` — бизнес-операции: `loadFullContestData` (пагинация страницами по 1000, аккумулятор на
    `Vector`, остановка при повторе уже полученных посылок и при лимите `cf-api.max-submissions` — защита от аномалий
    API), `fetchRecentStatuses`
  - `CodeforcesCache` — кэш на `zio.cache.Cache` + `Ref`: политики `refreshAfter` / `idleTtl`,
    warmup по `warmupContestIds`, `evictIdle`
- `cf.configs` — кейс-классы конфигурации (`derives ConfigReader`) + `AppConfig`: приватный слой
  `loadOrThrow` и публичные проекции `*.project(...)` под каждую секцию
- `cf.models` — модели API (`CfModels.scala`: `CfApiResponse`, `CfSubmission`, ...),
  `ContestState` (`Map[id -> submission]`, upsert при merge, дедупликация), `ContestEntry`
- `cf.routes` — `ContestsRoutes` (`GET /api/contests/{id}`), `MetricsRoutes` (`GET /metrics`),
  `HealthRoutes` (`GET /ping`), `StaticRoutes` (отдача SPA из classpath, защита от path traversal,
  fallback на index.html), `HttpRoutes` — агрегация всех роутов в один `Routes[Any, Response]`,
  при `cors.enabled` оборачивание всего дерева в `Middleware.cors` (см. `CorsConfig`)
- `cf.metrics.MetricsCollector` — **синглтон** `PrometheusMeterRegistry`, gauge-метрики `cf_user_*`
  по хендлам/задачам/языкам (обновление через `AtomicReference` в `ConcurrentHashMap`)
- `cf.utils` — `RateLimiter` (слой поверх rezilience, есть `noop`-реализация для тестов),
  `LanguageNormalizer` (нормализация языков посылок к каноническим группам)

Тесты зеркалят пакеты в `src/test/scala`. Тестовая инфраструктура — в `cf.utils`:
`MockedSpec` (сервис из мока через `testWithMock`), `RoutesSpec` (HTTP-тесты без поднятия сервера:
`routes.runZIO(request)`), `ConfigSpec` (парсинг HOCON-строк: валидный кейс + `invalidCases`);
тестовые данные — `cf.models.Fixtures`.

## Сборка и запуск

```bash
sbt compile        # компиляция
sbt lint           # проверка форматирования (scalafmtCheckAll, тот же шаг гейтом в CI)
sbt test           # тесты (зелёные на момент написания)
sbt run            # запуск Main; требуются env-переменные (см. ниже)

sbt Docker/publishLocal   # собрать Docker-образ локально
sbt Docker/publish        # опубликовать в Docker Hub (env: DOCKER_USERNAME, DOCKER_TAG)
sbt doc                   # Scaladoc (настройки в project/ScaladocSettings.scala)
```

Для `sbt run` нужны переменные окружения `HTTP`, `PORT`, `KEY`, `SECRET` — они подставляются в
`src/main/resources/application.conf` (`http-server.host/port`, `cf-api.key/secret`). Значения
обычно берутся из `.env` в корне (в `.gitignore`, подхватывается run-конфигурацией IDEA
`.run/runMain.run.xml`). Секция `cf-api` обязательна: подстановки `${KEY}`/`${SECRET}` разрешаются при
загрузке конфигурации — без этих переменных приложение не стартует.

Docker-образ: база `eclipse-temurin:25.0.5_8-jre-noble` (закреплённая версия, не плавающий тег),
порт 8080, JVM-флаги `-XX:MaxRAMPercentage=75.0`, `+UseZGC` (на JDK 25 это Generational ZGC;
`-XX:+UseContainerSupport` убран — no-op с JDK 10). Имя скрипта запуска — `cf-analysis`
(`executableScriptName`). Контейнер работает под непривилегированным
пользователем `app` (uid/gid 1001, `Docker / daemonUser[Uid]`/`daemonGroup[Gid]` в `project/DockerSettings.scala`;
без явного gid плагин пропускает создание группы и `COPY --chown=app:app` падает). В корне лежит
`.dockerignore` (VCS/IDE/target/секреты/локальная инфраструктура наблюдения) — для сборок из корня
репозитория, сам sbt-native-packager собирает контекст в `target/out/.../docker/stage`.

## Тестирование

- Фреймворк — zio-test (подключён в `build.sbt` через `ZTestFramework`), моки — scalamock-zio.
- Каждый спек — `object ...Spec extends ZIOSpecDefault` (или `MockedSpec` / `RoutesSpec` /
  `ConfigSpec`), результаты выводятся иерархически.
- Моки создаются заново на каждый тест (`envLayer`/`mockM` провайдится внутри `test`), разделять
  моки между тестами нельзя.
- Временная логика тестируется через `TestClock.adjust` (см. `RateLimiterSpec`, `CodeforcesCacheSpec`).
- Кэш в тестах конфигурируется маленькими значениями TTL (см. `baseConfig` в `CodeforcesCacheSpec`).
- Внешний API Codeforces в тестах не дёргается — мокается `CodeforcesClient`/`CodeforcesService`.
- В sbt 2.x `sbt test` — инкрементальный (прогоняет testQuick), при «зелёном» прошлом прогоне
  может запустить 0 тестов. Полный прогон вручную: `sbt "Test / testFull"`.

## Стиль кода

- Форматирование — scalafmt 3.8.6 (`.scalafmt.conf`): диалект scala3, `maxColumn = 120`,
  `trailingCommas = "always"`, `align.preset = none`, сортировка импортов (группы: библиотеки
  `zio.*`/`scala.*`/..., затем `java.*`/`javax.*`). Перед коммитом прогоняйте `sbt scalafmtAll`.
- Компилятор (`project/Dependencies.scala`): неиспользуемые импорты (`-Wunused:imports`) и
  неполный `match` (`-Wconf:msg=not be exhaustive:e`) — **ошибки компиляции**, не предупреждения.
- Комментарии и Scaladoc — на русском, в стиле существующего кода (короткий Scaladoc у каждого
  класса/объекта, объясняющий роль и взаимосвязи).
- Зависимости между компонентами — только через `ZLayer` в стиле Main: конструкторы классов принимают
  уже разрешённые зависимости, фабрики слоёв — в компаньонах.

## Конфигурация

`src/main/resources/application.conf`, секция `app`:

- `cache` — `capacity`, `ttl`, `idle-ttl`, `refresh-after`, `recent-count`, `reaper-interval`,
  `warmup-contest-ids` (стартовые значения: capacity 100, ttl 1h, idle-ttl 15m, refresh-after 1m)
- `http-server` — `host`, `port` (из env), лимиты заголовков/initial line, `keep-alive`, таймауты
- `rate-limiter` — `max` запросов за `interval` (по умолчанию 1 запрос / 2 секунды)
- `cf-api` — `key`, `secret` (из env), `log-full-uri` (дефолт `false`: логировать полный URI с секретами — только
  для отладки), `max-submissions` (лимит полной загрузки контеста в посылках, дефолт 200k: при достижении пагинация
  останавливается, возвращается накопленное) и вложенный `http-client`: таймауты (`request-timeout` — read-таймаут
  запроса, `connection-timeout` — установка соединения), retry (`retry-base-delay`, `retry-max-delay`,
  `retry-max-attempts`), `circuit-breaker`
- `circuit-breaker` (типизированный ридер, см. `CircuitBreakerConfig`) — `type` (`failure-count` | `failure-rate`),
  общий `reset-timeout` и блок `parameters` с настройками стратегии: `max-failures` для `failure-count`;
  `failure-rate-threshold` (0..1) и опциональные `sample-duration`/`min-throughput` (дефолты как в rezilience) для
  `failure-rate`; неудачами считаются только транзиентные ошибки
- `cors` — `enabled`, `allowed-origins`, `allowed-methods`, `allowed-headers`, `max-age`
  (по умолчанию выключено: SPA отдаётся с того же origin; origin разрешается точным совпадением строки)

Важно про Codeforces API (из Readme.md): чтобы `contest.status` работал с `asManager=true`,
владелец apiKey должен быть **менеджером контеста**, иначе API отвечает, будто контеста не существует.

## CI/CD и деплой

- `.github/workflows/pipeline.yml` (на пуш в любую ветку):
  - `testing` — `sbt test` (JDK 25 temurin, кэш sbt; общий шаг — `.github/actions/setup-scala`)
  - `documentation` (только `master`) — `sbt doc`, публикация на GitHub Pages
  - `publish` — `sbt Docker/publishLocal` (сборка образа без пуша) + два вызова общего composite action
    `.github/actions/docker-push` (логин → tag → push, вход `tags` — список тегов через пробел): в Docker Hub (с любой
    ветки) и в GitHub Packages **только с master** (`ghcr.io/slavabuchnev/cf-analysis`, имя репозитория приводится к
    lowercase — GHCR его требует); тег: `master-latest` для master, иначе `dev-latest`, плюс в ghcr.io мутабельный
    `latest` — указатель на последний зелёный master (для откатов использовать неизменяемый `master-latest`)
    (env `DOCKER_TAG`, `DOCKER_USERNAME`; пароль Docker Hub — secret `DOCKER_PASSWORD`, в ghcr.io — `GITHUB_TOKEN`
    с правом `packages: write`)
- `.github/workflows/deploy.yml` — ручной запуск: у сервиса Render обновляется image path и
  стартует деплой через hook, затем статус деплоя поллится до `live` (до 20 минут).
  Secrets: `RENDER_API_KEY`, `RENDER_DEV_SERVICE_ID`, `RENDER_DEV_DEPLOY_HOOK_URL`, `DOCKER_USERNAME`.
- Readme.md указывает живой адрес `https://cf-analysis.up.railway.app/` и бейдж внешнего healthcheck
  cron-job.org — это более старая площадка; актуальный деплой описан в `deploy.yml` (Render).

⚠️ Команды из Readme `sbt ciGenerateGithubWorkflow` и `sbt ciCheckGithubWorkflow` в текущей сборке
**не определены** (плагина sbt-github-actions нет, workflow'ы правятся вручную). `sbt lint` определён —
это `scalafmtCheckAll`; локальная проверка — `sbt lint` + `sbt test`.

## Мониторинг

- `GET /metrics` отдаёт scrape в формате Prometheus из единого `PrometheusMeterRegistry`.
- `docker-compose.yaml` поднимает для локального наблюдения: Prometheus (порт 9090, конфиг
  `prometheus.yml` скрейпит `host.docker.internal:8080`) и Grafana (порт 3000, admin/admin;
  provisioning датасорсов и дашбордов из `grafana/provisioning/`). Дашборд `init.json` — на русском,
  метрики `cf_user_*`. Образы в compose закреплены конкретными версиями (`prom/prometheus:v3.16.0`,
  `grafana/grafana:13.3.1`), не `latest`.
- Пустая директория `datasource.yml/` в корне репозитория — артефакт, не используется; реальный
  datasource — `grafana/provisioning/datasources/datasource.yml`.

## Подводные камни

- `CodeforcesClient` считает ответ сбойным, если тело начинается с `<` (HTML от Cloudflare вместо JSON).
- `LanguageNormalizer.normalize` — цепочка `case` по порядку: ветка `s.contains("D") => "D"` стоит
  ближе к концу и перехватывает любые строки с буквой «D», не пойманные раньше; новые языки
  добавляйте **выше** этой ветки.
- `MetricsCollector.registry` — глобальный синглтон (не per-fiber/per-test): gauge накапливаются
  в реестре, между тестами не сбрасываются.
- При старте sbt печатает lint-warning про неиспользуемый ключ `rpmScriptletsDirectory` —
  шум от sbt-native-packager, на сборку не влияет (часть подобных ключей уже в `excludeLintKeys`).
- В репозитории несколько веток `feat/*`, `bug/*`; основная — `master`.
