# cf-analysis

![Pipeline](https://github.com/SlavaBuchnev/cf-analysis/actions/workflows/pipeline.yml/badge.svg)
![Cron job status](https://api.cron-job.org/jobs/8838436/a85759c3aceb5a69/status-1.svg)

Сервис аналитики контестов Codeforces. Загружает посылки контеста через публичное API Codeforces, кэширует их
с инкрементальным обновлением и отдаёт:

- **JSON-API** для встроенного фронта — `GET /api/contests/{id}` (отсортированный список посылок контеста);
- **метрики Prometheus** — `GET /metrics` (gauge `cf_user_*` по хендлам, задачам и языкам);
- **статический SPA** — `/` (vanilla JS + Chart.js: таблицы и графики по контесту).

Live: https://cf-analysis.up.railway.app/ — старое развёртывание; актуальный деплой — на Render
(`.github/workflows/deploy.yml`, запуск вручную).

## Технологии

Scala 3 / ZIO 2 (zio-http, zio-cache, zio-json), sttp client4, rezilience (rate limit, retry, circuit breaker),
Micrometer + Prometheus, sbt-native-packager (Docker).

## Локальная разработка

Требования: JDK 25, sbt. Нужны переменные окружения `HTTP`, `PORT`, `KEY`, `SECRET` (подставляются в
`application.conf`, обычно из `.env` в корне) — без них приложение не стартует.

```bash
sbt compile   # компиляция
sbt lint      # проверка форматирования (scalafmtCheckAll), тот же шаг гейтом в CI
sbt test      # тесты; полный прогон вручную: sbt "Test / testFull"
sbt run       # запуск Main
sbt doc       # Scaladoc
```

## Запуск Docker-образа

Готовые образы публикуются на каждый пуш (зелёный pipeline):

- **GitHub Packages** (только master): `ghcr.io/slavabuchnev/cf-analysis:latest` — последний зелёный master;
  `master-latest` — тот же образ под неизменяемым тегом (для откатов);
- **Docker Hub**: `<dockerhub-user>/cf-analysis:master-latest` (master) или `:dev-latest` (остальные ветки).

### Параметры (переменные окружения)

| Параметр | Описание |
|---|---|
| `KEY` | apiKey Codeforces API (обязателен; владелец ключа должен быть менеджером контестов) |
| `SECRET` | секрет Codeforces API (обязателен) |
| `HTTP` | хост прослушивания сервера, в контейнере — `0.0.0.0` (обязателен) |
| `PORT` | порт сервера, в контейнере — `8080` (обязателен) |

### Пример

```bash
docker run -d --name cf-analysis \
  -p 8080:8080 \
  -e KEY=<apiKey> -e SECRET=<secret> \
  -e HTTP=0.0.0.0 -e PORT=8080 \
  ghcr.io/slavabuchnev/cf-analysis:latest
```

Проверка: `curl localhost:8080/ping` → `200 OK`; метрики Prometheus — `GET /metrics`; JSON-API контеста —
`GET /api/contests/{id}`.

Внутри образа приложение стартует под непривилегированным пользователем с JVM-флагами
`-XX:MaxRAMPercentage=75.0 -XX:+UseZGC` — лимиты памяти контейнера подхватываются автоматически.

## Примечания

- `contest.status` вызывается с `asManager=true`: владелец `apiKey` должен быть **менеджером контеста**, иначе
  API отвечает, будто контеста не существует.
- CI (`.github/workflows/pipeline.yml`): `testing` на каждый пуш, `documentation` (Scaladoc на GitHub Pages)
  и публикация образов — с master.
