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
