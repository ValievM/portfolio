# Документация Phone Agent

Android-приложение на Kotlin: чат с ИИ-моделью, будильники, поиск файлов и уведомления.

Начните с [обзора архитектуры](architecture/overview.md) и [описания продукта](product/overview.md).

## Проверки и эксплуатация

- [Установка и первый вход](INSTALL.md)
- [Заметки о входе и бэкенде (результат этапа 0)](backend-notes.md)
- [Аудит логов (этап 5.5)](log-audit.md)
- [Ручная проверка «Стыка» на телефоне](manual-verification-brand.md)
- [Ручная проверка: инструменты, вход, сборка, CI](manual-verification-tools-build.md)
- [Ручная проверка на реальном телефоне](manual-verification.md)
- [Замеры: память, батарея, фоновая работа](measurements.md)

## Архитектурные решения

- [ADR 0001: базовые решения проекта](adr/0001-baseline.md)
- [ADR 0002: основной вход через подписку (GO)](adr/0002-subscription-login-go.md)
- [ADR 0003: поиск файлов через MediaStore](adr/0003-global-file-search.md)
- [ADR 0004: одно подтверждение на пачку вызовов](adr/0004-batch-approval.md)
- [ADR 0005: домен разговора](adr/0005-conversation-domain.md)
- [ADR 0006: фирменный стиль «Стык»](adr/0006-brand-joint.md)

## Архитектура

- [Адаптеры](architecture/adapters.md)
- [Эпик «Домен разговора»: проектирование](architecture/conversation-domain-design.md)
- [Ядро (`:core:domain`)](architecture/core.md)
- [Решения и риски архитектуры](architecture/decisions.md)
- [Карта архитектуры: `:app`, мост диалогов, вход, хранилище, логи](architecture/map-app-infra.md)
- [Карта архитектуры: вход и токены (`:data:auth`)](architecture/map-auth.md)
- [Карта архитектуры: ядро и клиент модели](architecture/map-core-data.md)
- [Карта архитектуры: потоки выполнения](architecture/map-flows.md)
- [Карта архитектуры: обзор](architecture/map-overview.md)
- [Карта архитектуры: инструменты и UI чата](architecture/map-tools-app.md)
- [Архитектура: обзор](architecture/overview.md)
- [Паттерны, конкурентность, DI](architecture/patterns.md)
- [Рецепты и конвенции кода](architecture/recipes.md)
- [Тестирование, TDD и защита архитектуры](architecture/testing.md)
- [UI](architecture/ui.md)

## Интерфейс

- [Доступность и безопасность интерфейса](design/a11y-security.md)
- [Фирменный стиль «Стык»](design/brand.md)
- [Токены, компоненты, экраны](design/components.md)
- [Иконки, движение, тексты интерфейса](design/icons-motion-copy.md)
- [Правила вёрстки (Compose)](design/layout-rules.md)
- [Библиотеки и инструменты](design/libraries.md)
- [Дизайн: обзор](design/overview.md)
- [Проверка интерфейса и решения по дизайну](design/qa.md)
- [Визуальный стиль: цвет, шрифты, формы](design/style.md)

## Функциональность

- [ТЗ: обзор MVP](product/overview.md)
- [Функциональные требования](product/requirements.md)

## Протокол и интеграция

- [Что выяснено (факты и статус)](research/backend-facts.md)

## Сборка и CI

- [Оптимизация сборки](tooling/build-performance.md)
- [CI и запуск Gradle](tooling/ci.md)
