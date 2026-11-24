# cf-analysis

![Pipeline](https://github.com/SlavaBuchnev/cf-analysis/actions/workflows/pipeline.yml/badge.svg)
![Cron job status](https://api.cron-job.org/jobs/8838436/a85759c3aceb5a69/status-1.svg)

Сервис аналитики контестов Codeforces в виде готового Docker-образа: загружает посылки контеста через публичное API
Codeforces, кэширует их с инкрементальным обновлением и отдаёт:

- **JSON-API** — `GET /api/contests/{id}` (отсортированный список посылок контеста);
- **метрики Prometheus** — `GET /metrics` (gauge `cf_user_*` по хендлам, задачам и языкам);
- **статический SPA** — `/` (vanilla JS + Chart.js: таблицы и графики по контесту).

## Образы

| Реестр | Адрес | Теги |
|---|---|---|
| GitHub Packages | `ghcr.io/slavabuchnev/cf-analysis` | `latest`, `master-latest` (только master) |
| Docker Hub | `<dockerhub-user>/cf-analysis` | `master-latest` (master), `dev-latest` (остальные ветки) |

- `latest` — мутабельный указатель на последний зелёный master (есть только в ghcr.io, куда пушится только master);
- `master-latest` — тот же образ под неизменяемым тегом конкретного прогона: для откатов и фиксации «что сейчас в проде».

## Быстрый старт

```bash
docker run -d --name cf-analysis \
  -p 8080:8080 \
  -e KEY=<apiKey> -e SECRET=<secret> \
  -e HTTP=0.0.0.0 -e PORT=8080 \
  ghcr.io/slavabuchnev/cf-analysis:latest
```

### Параметры (переменные окружения)

| Параметр | Описание |
|---|---|
| `KEY` | apiKey Codeforces API (обязателен) |
| `SECRET` | секрет Codeforces API (обязателен) |
| `HTTP` | хост прослушивания сервера, в контейнере — `0.0.0.0` (обязателен) |
| `PORT` | порт сервера, в контейнере — `8080` (обязателен) |

Владелец `apiKey` должен быть **менеджером контеста** (запросы идут с `asManager=true`) — иначе API отвечает,
будто контеста не существует.

### Проверка работы

```bash
curl localhost:8080/ping                  # 200 OK — сервис жив
curl localhost:8080/metrics               # scrape для Prometheus
curl localhost:8080/api/contests/551173   # посылки контеста (JSON)
```

### Внутри образа

Приложение стартует под непривилегированным пользователем (`app`, uid/gid 1001) с JVM-флагами
`-XX:MaxRAMPercentage=75.0 -XX:+UseZGC` — лимиты памяти контейнера подхватываются автоматически.
База: `eclipse-temurin:25-jre`, порт `8080`.

## Ссылки

- Live: https://cf-analysis.up.railway.app/ (старое развёртывание; актуальный деплой — Render, `.github/workflows/deploy.yml`, ручной запуск);
- Разработка и локальный запуск: [Contributing.md](Contributing.md);
- Устройство проекта, стиль, конфигурация: [AGENTS.md](AGENTS.md).
