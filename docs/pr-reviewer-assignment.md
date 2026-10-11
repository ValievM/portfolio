# PR Reviewer Assignment · Go

Сервис автоматически назначает до двух активных ревьюеров из команды автора PR. Поддерживает переназначение, управление активностью пользователей и идемпотентный merge.

## Маршрут ревью

[Доменные сущности](../projects/pr-reviewer-assignment/internal/core/domain/) → [сервисы](../projects/pr-reviewer-assignment/internal/core/services/) → [порты](../projects/pr-reviewer-assignment/internal/core/ports/) → [HTTP и PostgreSQL](../projects/pr-reviewer-assignment/internal/adapters/) → [тесты](../projects/pr-reviewer-assignment/tests/).

## Запуск

Требуются Docker Compose, для локальных тестов — Go версии из `go.mod` и Make.

```bash
cd projects/pr-reviewer-assignment
docker compose up -d --build
curl http://localhost:8080/health
```

Включённый `.env` содержит настройки локальной демонстрационной БД. Они не предназначены для production.

```bash
# Интеграционные и E2E-тесты
docker compose up -d postgres_test
make test-integration
make test-e2e
```

Отдельных unit-тестов в импортированном снимке нет; CI запускает имеющиеся интеграционные и E2E-тесты с PostgreSQL.

[Нагрузочный сценарий k6](../projects/pr-reviewer-assignment/scripts/load_test/k6.js). Утверждение p95 < 300 мс есть в исходном README, но отдельного воспроизводимого отчёта здесь нет. Покрытие > 75% также не подтверждено сохранённым отчётом.

[Полная документация](../projects/pr-reviewer-assignment/README.md) · [исходный репозиторий](https://github.com/ValievM/pr-reviewer-assignment).
