# Библиотеки и инструменты

> См. также: [обзор](overview.md) · [проверка](qa.md)

**Берём**

| Что | Зачем | Заметка |
|---|---|---|
| Compose BOM + Material 3 (стабильная линия 1.4.x) | основа UI | 1.5.0 всё ещё в альфе (alpha28 на 09.09.2026); Expressive-компоненты там. Возвращаемся, когда станет стабильной |
| Navigation 3 (`navigation3-runtime`, `-ui`) | навигация: `NavKey` + `NavDisplay` | стабильная (1.1.x), 1.2 в RC; predictive back из коробки |
| `activity-compose` | `enableEdgeToEdge()` | |
| `core-splashscreen` | заставка | |
| `multiplatform-markdown-renderer` **0.39.2**: `-m3`, `-code` | markdown, потоковый режим, подсветка кода | версия 0.x, API может меняться: закрываем одной обёрткой `AssistantMarkdown`; версию фиксируем. **Не 0.43.0** (проверено 23.09.2026): версии 0.42+ требуют `compileSdk 37` (наш AGP 8.13 поддерживает до 36), а 0.40+ собраны под Java 21, и юнит-тесты на JDK 17 их не загружают. Транзитивная `dev.snipme:highlights` 1.1.0 тоже под Java 21, поэтому в `app/build.gradle.kts` закреплена 1.0.0 (Java 8). Обновляемся вместе с AGP/JDK |
| `compose-rules` (mrmans0n) для ktlint/detekt | проверка Compose-кода (порядок параметров, `modifier`, hoisting, превью) | подключаем на этапе 1 |
| Compose UI tooling (debug) | превью, Layout Inspector | |
| Roborazzi + Robolectric | скриншот-тесты ключевых компонентов | популярен, но версию и настройку **[проверить, этап 5]** |
| LeakCanary (debug) | поиск утечек памяти | предложение **[проверить]** |
| Палитра «Стык» | цвета и статусные роли из [brand/joint-tokens.json](brand/joint-tokens.json) | без библиотеки; тест сверяет код с JSON |
| Шрифты: Inter + JetBrains Mono | типографика | вшитые variable, OFL |
| Material Symbols Rounded | иконки | ~20 vector drawable, Apache 2.0 |

**Не берём (и почему)**

| Что | Почему |
|---|---|
| `material-icons-extended` | тяжёлая, нужно ~20 иконок, стилистически старый набор |
| Coil и загрузка картинок | внешних картинок нет и не должно быть (см. [a11y-security.md](a11y-security.md)) |
| Lottie | декор, лишний вес и энергия |
| Accompanist, Timber | устарели или заменены (`AppLogger`, штатные API) |
| MaterialKolor | нужен для генерации палитр «на лету»; нам хватает фиксированной палитры «Стык» |
| `kotlinx-collections-immutable` | пока не нужен (см. strong skipping) |
| Material 3 Expressive | альфа-версия |
| `material3-adaptive` | планшетная раскладка не в MVP, хватает ограничения ширины |
| Haze и эффекты размытия | дорого по энергии, не нужно |
| Скачиваемые Google Fonts | нет variable-шрифтов, нужна сеть и Google-сервисы |
| Figma | для MVP дизайн в коде (превью) быстрее |

**Позже:** Baseline Profiles (этап 6, если замеры запуска плохие); Expressive-компоненты (когда 1.5 станет стабильной); планшеты и складные устройства.

**Ориентиры:** примеры Google [Reply](https://github.com/android/compose-samples/tree/main/Reply) (адаптивность, Material 3) и Now in Android (структура и дизайн-система). Паттерны чатов: стриминг, видимые вызовы инструментов, кнопка «Стоп», закреплённый композер, подтверждение перед необратимым.
