![Cron job status](https://api.cron-job.org/jobs/8838436/a85759c3aceb5a69/status-1.svg)

Чтобы использовать contest.status и он не отдавал ошибками надо:
- Чтобы пользователь был в группе и был менеджером контеста(если не будет менеджером будет возвращать что контеста нет)

```bash
# Локально прогнать те же проверки, что и в CI
sbt lint        # проверка форматирования (scalafmtCheckAll)
sbt test        # тесты
```
```
https://cf-analysis.up.railway.app/
```

## Запуск Docker-образа

Готовые образы публикуются на каждый пуш (зелёный pipeline):
- **GitHub Packages** (только master): `ghcr.io/slavabuchnev/cf-analysis:latest` — последний зелёный master, `master-latest` — тот же образ под неизменяемым тегом (для откатов);
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

Проверка: `curl localhost:8080/ping` → `200 OK`; метрики Prometheus — `GET /metrics`; JSON-API контеста — `GET /api/contests/{id}`.

Внутри образа приложение стартует под непривилегированным пользователем с JVM-флагами `-XX:MaxRAMPercentage=75.0 -XX:+UseZGC` — лимиты памяти контейнера подхватываются автоматически.
