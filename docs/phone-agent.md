# Phone Agent · Android / Kotlin

Чат с моделью и узкие инструменты управления телефоном: будильник, поиск файлов, уведомления. Проект развивается; ручная приёмка отдельных сценариев отражена в [чек-листе](../projects/phone-agent/docs/manual-verification.md).

## Инженерные решения

- Пять модулей: `app`, `core:domain`, `data:llm`, `data:auth`, `tools`. Ядро не зависит от Android.
- Compose и Material 3, потоковые ответы, история диалогов в Room.
- OAuth с PKCE, обновление токенов, шифрование с Android Keystore.
- Подтверждение действий с последствиями и ограничения вызовов инструментов.
- Unit-, Robolectric- и Compose UI-тесты, архитектурные проверки; detekt, ktlint через Spotless, Android Lint.

## Маршрут ревью

[Архитектура](../projects/phone-agent/docs/architecture/overview.md) → [ядро](../projects/phone-agent/core/domain/) → [авторизация](../projects/phone-agent/data/auth/) → [инструменты](../projects/phone-agent/tools/) → [UI](../projects/phone-agent/app/src/main/) → [подход к тестам](../projects/phone-agent/docs/architecture/testing.md).

## Запуск

Откройте `projects/phone-agent` как отдельный проект в Android Studio. Требуются JDK 17 и Android SDK; используйте версии SDK, заданные в `build-logic` проекта. Настройте путь к SDK через Android Studio (локальный `local.properties` не коммитится).

```bash
cd projects/phone-agent
./gradlew assembleDebug
./gradlew check
python3 scripts/check_docs.py
```

Для UI-тестов нужен подключённый телефон или эмулятор:

```bash
./gradlew connectedDebugAndroidTest
```

Параметры входа и ограничения бэкенда: [backend-notes](../projects/phone-agent/docs/backend-notes.md). Настройка пользователя и этапы: [индекс документации](../projects/phone-agent/docs/README.md). Ключи и токены вводятся в приложении, в Git их хранить нельзя.
