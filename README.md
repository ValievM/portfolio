# Портфолио · Kotlin / Go

Android-приложение с ИИ-инструментами и два backend-сервиса на Go. В каждом проекте — полный исходный код, отдельная сборка и документация.

| Проект | Задача | Технологии | Начать ревью |
|---|---|---|---|
| **[Phone Agent](projects/phone-agent/)** | Чат с ИИ, будильники, поиск файлов и уведомления | Kotlin, Compose, Coroutines, Flow, Room, Hilt, OkHttp | [Обзор и запуск](docs/phone-agent.md) |
| **[PR Reviewer Assignment](projects/pr-reviewer-assignment/)** | Автоматическое назначение и замена ревьюеров | Go, PostgreSQL, Docker, k6 | [Обзор и запуск](docs/pr-reviewer-assignment.md) |
| **[GoShop](projects/goshop/)** | API интернет-магазина: каталог, авторизация, корзина, заказы | Go, PostgreSQL, Redis, S3, Google OAuth2, JWT | [Обзор и запуск](docs/goshop.md) |

## Что посмотреть за 5 минут

1. **Android:** [границы модулей](projects/phone-agent/settings.gradle.kts), [чистое Kotlin-ядро](projects/phone-agent/core/domain/), [авторизация и шифрование токенов](projects/phone-agent/data/auth/).
2. **Backend и тесты:** [бизнес-логика назначения ревьюеров](projects/pr-reviewer-assignment/internal/core/services/), [интеграционные и E2E-тесты](projects/pr-reviewer-assignment/tests/), [нагрузочный сценарий](projects/pr-reviewer-assignment/scripts/load_test/k6.js).
3. **Backend и инфраструктура:** [домен GoShop](projects/goshop/internal/core/), [адаптеры](projects/goshop/internal/adapters/), [API](projects/goshop/docs/swagger/swagger.yaml).

## Работа с репозиторием

```bash
git clone https://github.com/ValievM/portfolio.git
cd portfolio
```

Дальше перейдите в папку выбранного проекта и следуйте его инструкции выше. Проекты запускаются независимо: общих зависимостей и общего приложения нет. Backend-сервисы по умолчанию занимают порт 8080 — запускайте их по очереди.

## Проверки и происхождение кода

[GitHub Actions](https://github.com/ValievM/portfolio/actions) запускает Android `check`, проверку Android-документации, интеграционные и E2E-тесты сервиса ревьюеров на PostgreSQL и сборку GoShop. Android UI-тесты запускаются отдельно на телефоне или эмуляторе по инструкции проекта.

Проекты импортированы как снимки исходных репозиториев. Точные коммиты и дата импорта: [sources.json](sources.json). История разработки, issues и обсуждения доступны в исходных репозиториях; следующие изменения этого портфолио ведутся здесь. Автоматической синхронизации нет.

Численные результаты производительности и покрытия требуют отчётов с условиями измерений. В этом портфолио они не заявлены как подтверждённые метрики.
