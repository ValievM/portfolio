# Документация проекта

Android-приложение: чат с Codex + узкие инструменты для управления телефоном (будильник, поиск файлов, уведомление). Нативный Kotlin, разработка по TDD.

**Вся документация лежит в `docs/`.** Единственное исключение — [`AGENTS.md`](../AGENTS.md) в корне репозитория: Codex подхватывает его, только если он лежит в корне (или на пути от корня к рабочей папке). Это файл настроек для агента, а не документация: в нём короткие правила и ссылки сюда.

## Статус
Архитектура, дизайн, требования и этапы **утверждены 21.09.2026**: [ADR 0001](adr/0001-baseline.md) (что решено, что проверено, что остаётся непроверенным и на каком этапе закрывается). Допущения с пометкой `[проверить]` до своего этапа считаются гипотезами.

## Как читать (и почему файлов много)
Документация разбита на небольшие тематические файлы (каждый до ~14 КБ), чтобы человек и агент читали только нужное: на одну задачу обычно хватает 2-4 файлов. Всё, что прочитано, остаётся в контексте агента и стоит токенов, поэтому «прочитать всё подряд» не нужно.

**Стартовая точка:** файл текущего этапа из [stages/](stages/README.md). В нём есть блок «Читать перед началом» со ссылками на нужные документы.

## Роутер: задача → что читать

| Задача | Читать |
|---|---|
| Работа над этапом | [stages/README.md](stages/README.md) → файл этапа |
| Что входит в MVP, критерии приёмки, риски | [product/overview.md](product/overview.md) |
| Требования: вход, клиент модели, цикл агента, инструменты, экраны | [product/requirements.md](product/requirements.md) |
| Константы входа и бэкенда (источник истины) | [backend-notes.md](backend-notes.md) |
| Модули, куда класть код | [architecture/overview.md](architecture/overview.md) |
| Порты, `AgentLoop`, инструменты, ошибки | [architecture/core.md](architecture/core.md) |
| Клиент модели, вход, инструменты Android, мост диалогов, хранилище | [architecture/adapters.md](architecture/adapters.md) |
| ViewModel и состояние экрана | [architecture/ui.md](architecture/ui.md) |
| Паттерны, корутины, DI | [architecture/patterns.md](architecture/patterns.md) |
| Тесты, TDD, автоматические правила границ | [architecture/testing.md](architecture/testing.md) |
| Добавить инструмент, способ входа, экран | [architecture/recipes.md](architecture/recipes.md) |
| Почему так решили, риски архитектуры | [architecture/decisions.md](architecture/decisions.md) |
| Домен разговора: решение и план T8 → T9 → T7 | [ADR 0005](adr/0005-conversation-domain.md) · [детальный дизайн](architecture/conversation-domain-design.md) |
| Карта фактической архитектуры для ревью (снимок 24.09, строки устарели): граф модулей, DI, диаграммы классов, потоки, наблюдения | [architecture/map-overview.md](architecture/map-overview.md) → [ядро](architecture/map-core-data.md), [вход](architecture/map-auth.md), [tools и UI](architecture/map-tools-app.md), [app](architecture/map-app-infra.md), [потоки](architecture/map-flows.md) |
| Обзор дизайна | [design/overview.md](design/overview.md) |
| Цвет, шрифты, формы | [design/style.md](design/style.md) |
| Иконки, анимации, тексты интерфейса | [design/icons-motion-copy.md](design/icons-motion-copy.md) |
| Токены, компоненты, экраны | [design/components.md](design/components.md) |
| Вёрстка Compose: списки, insets, состояния | [design/layout-rules.md](design/layout-rules.md) |
| Доступность и безопасность UI (markdown, ссылки) | [design/a11y-security.md](design/a11y-security.md) |
| Библиотеки и инструменты UI | [design/libraries.md](design/libraries.md) |
| Скриншот-тесты, Design QA | [design/qa.md](design/qa.md) |
| Задачи и решения владельца одним экраном | [OWNER-TASKS.md](../OWNER-TASKS.md) (корень репозитория) |
| Установка и первый вход для пользователя | [INSTALL.md](INSTALL.md) |
| Что из сделанного пользователь ещё не проверил на своём телефоне | [manual-verification.md](manual-verification.md) (чат и ход агента), [manual-verification-tools-build.md](manual-verification-tools-build.md) (инструменты, вход, сборка, CI, логи), [manual-verification-brand.md](manual-verification-brand.md) (вид, размеры, движение «Стыка») |
| Что пишется в логи и как это проверять | [log-audit.md](log-audit.md) |
| Context7 (документация библиотек) | [tooling/context7.md](tooling/context7.md) |
| Оптимизация сборки (замечания на будущее) | [tooling/build-performance.md](tooling/build-performance.md) |
| Замеры: память, батарея, фоновая работа | [measurements.md](measurements.md) |
| Фирменный стиль «Стык» (цвет, знак, тон) | [design/brand.md](design/brand.md) |
| CI и JDK для Gradle | [tooling/ci.md](tooling/ci.md) |
| Трекер задач: GitHub Issues, метки, жизненный цикл, промпт агенту | [tooling/tracker.md](tooling/tracker.md) |
| Команда из трёх GPT: зоны, кольцо проверки, замок Gradle | [tooling/swarm.md](tooling/swarm.md), промпт [GPT](tooling/swarm-prompt-gpt.md) |

## Карта документов
- `product/`: что строим. [overview](product/overview.md), [requirements](product/requirements.md).
- [INSTALL.md](INSTALL.md): как поставить приложение и войти (для пользователя).
- `research/`: исходные гипотезы. [backend-facts](research/backend-facts.md). Проверенные значения: [backend-notes](backend-notes.md).
- `architecture/`: как устроено. [overview](architecture/overview.md), [core](architecture/core.md), [adapters](architecture/adapters.md), [ui](architecture/ui.md), [patterns](architecture/patterns.md), [testing](architecture/testing.md), [recipes](architecture/recipes.md), [decisions](architecture/decisions.md). Карта по коду (снята с `main`, для ревью): [map-overview](architecture/map-overview.md), [map-core-data](architecture/map-core-data.md), [map-auth](architecture/map-auth.md), [map-tools-app](architecture/map-tools-app.md), [map-app-infra](architecture/map-app-infra.md), [map-flows](architecture/map-flows.md).
- `design/`: как выглядит. [overview](design/overview.md), [style](design/style.md), [icons-motion-copy](design/icons-motion-copy.md), [components](design/components.md), [layout-rules](design/layout-rules.md), [a11y-security](design/a11y-security.md), [libraries](design/libraries.md), [qa](design/qa.md).
- `stages/`: в каком порядке делаем. [README](stages/README.md), [A](stages/a-environment.md), [0](stages/0-spike.md), [1](stages/1-skeleton.md), [2](stages/2-auth.md), [3](stages/3-model-client.md), [4](stages/4-tools.md), [5](stages/5-hardening.md), [6](stages/6-release.md).
- `tooling/`: инструменты агента. [context7](tooling/context7.md), [build-performance](tooling/build-performance.md), [ci](tooling/ci.md), [tracker](tooling/tracker.md), [swarm](tooling/swarm.md), [swarm-prompt-gpt](tooling/swarm-prompt-gpt.md).
- [manual-verification.md](manual-verification.md): что из сделанного реально потрогал пользователь на своём телефоне, а что нет. [log-audit.md](log-audit.md): аудит логов на токены и данные чата.
- [measurements.md](measurements.md): замеры этапа 6 и проверка отсутствия фоновой работы.
- `adr/`: принятые решения. [0001-baseline](adr/0001-baseline.md), [0002-subscription-login-go](adr/0002-subscription-login-go.md), [0003-global-file-search](adr/0003-global-file-search.md), [0004-batch-approval](adr/0004-batch-approval.md), [0005-conversation-domain](adr/0005-conversation-domain.md; принято), [детали эпика](architecture/conversation-domain-design.md), [0006-brand-joint](adr/0006-brand-joint.md).

## Правила ведения
- Новый документ создаём в `docs/`, не в корне и не рядом с кодом. Язык русский, имена файлов латиницей.
- Новый файл сразу добавляем в этот индекс. Один файл — одна тема, до ~14 КБ; `AGENTS.md` до 8 КБ (его читают при каждом запуске).
- Одно знание в одном месте. Ссылаемся на файл и заголовок, а не на номера разделов.
- Решение изменилось: документ обновляем **в том же коммите**, что и код.
- При расхождении приоритет такой: `backend-notes.md` (значения бэкенда) → `architecture/` (устройство) → `product/` (требования). Расхождение называем вслух и исправляем.
- Проверка ссылок, размеров и структуры: `python3 scripts/check_docs.py` (запускаем после любой правки документов).
