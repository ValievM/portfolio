# Замеры: память, батарея, фоновая работа

Документ этапа 6: что и как измерено. Приложение «Телефонный агент», версия `0.1.0` (`versionName` в `app/build.gradle.kts`). Проверка фоновой работы (6.3) сделана по коду и по итоговому манифесту релизной сборки без телефона. Замеры памяти (6.1) и батареи (6.2) требуют телефона и пока не делались.

Проверено на коммите: `7c6d6ad` (main), дата проверки 2026-09-25, манифест собран командой `./gradlew :app:processReleaseManifest`, итоговый файл `app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml`.

## 6.1 Память

Не измерено, нужен телефон.

## 6.2 Батарея

Не измерено, нужен телефон.

## 6.3 Фоновая работа

Вывод: **фоновой работы нет.** В манифесте нет наших служб, приёмников и провайдеров; компоненты библиотек либо не выполняют работу сами, либо запускаются системой в момент, когда приложение уже работает. Периодических запросов нет: сеть используется только по действию пользователя или во время входа. Ниже — доказательства.

### Компоненты итогового манифеста

| Компонент | Откуда | `exported` | Работает без открытого приложения? |
|---|---|---|---|
| `MainActivity` | наше | да (LAUNCHER) | Нет: это экран приложения, запускается пользователем. |
| `androidx.startup.InitializationProvider` | библиотека androidx.startup | нет | Нет: провайдер выполняется только при старте самого процесса приложения, инициализирует emoji2, ProcessLifecycleOwner и ProfileInstaller. |
| `androidx.room.MultiInstanceInvalidationService` | библиотека Room | нет | Нет: служба нужна только когда открыто несколько процессов приложения с одной базой; у нас процесс один (`ConversationStorageModule`, `Room.databaseBuilder`), служба никто не запускает и работать она не может. |
| `androidx.profileinstaller.ProfileInstallReceiver` | библиотека profileinstaller | да, но с `android.permission.DUMP` (системное разрешение) | Нет: приёмник ставит ART-профиль по команде инструментария (например, при установке из магазина). Он не выполняет работу приложения и наши данные не трогает. |

Разрешения: `INTERNET`, `POST_NOTIFICATIONS`, `SET_ALARM` (только отправка запроса в «Часы»), `READ_EXTERNAL_STORAGE` (до API 32), `READ_MEDIA_IMAGES/VIDEO/AUDIO`, `READ_MEDIA_VISUAL_USER_SELECTED` — все запрашиваются под действие пользователя. `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` — системный маркер для динамических приёмников, у нас их нет.

### Поиск по коду (воспроизводимые команды)

```
grep -rn "<service\|<receiver\|<provider" --include=AndroidManifest.xml .
```
В исходных манифестах модулей — ничего (компоненты из таблицы выше добавлены библиотеками при слиянии).

```
grep -rnE 'AlarmManager|JobScheduler|WorkManager|startService|startForegroundService|registerReceiver|WakeLock|Timer\(|scheduleAtFixedRate|postDelayed' --include='*.kt' app/src/main core/domain/src/main data/llm/src/main data/auth/src/main tools/src/main
```
Ни одного попадания.

```
grep -rn 'while (true)' --include='*.kt' core/domain/src/main data/auth/src/main
```
Два попадания, оба не фон:
- `AgentLoop.kt` (`:core:domain`): цикл шагов запроса; работает, только пока пользователь ждёт ответ в открытом чате, ограничен `AgentConfig.maxSteps` и отменяется кнопкой «Стоп».
- `ChatGptDeviceCodeAuth.kt` (`:data:auth`): опрос кода устройства; только пока пользователь проходит вход, ограничен таймаутом 15 минут.

```
grep -rn 'ProcessLifecycleOwner' --include='*.kt' data/auth/src/main
```
`ChatGptBrowserAuth`: используется только чтобы дождаться возврата приложения на передний план во время входа (окно сети после возврата из браузера) — это наблюдение за состоянием, не фоновая работа.

### Сеть

OkHttp создаётся в `app/di/AuthBindingsModule.kt` (`provideOkHttpClient`) и передаётся потребителям; клиент по умолчанию — в конструкторах `ResponsesGateway`, `OkHttpModelCatalog`, `OkHttpApiKeyValidationGateway` (`data/llm`), `OkHttpTokenRefreshGateway`, `OkHttpDeviceCodeGateway` (`data/auth`). Запросы идут только: из открытого чата по действию пользователя (`AgentLoop`), при входе (`SessionManager` через провайдеры входа), при проверке ключа, при загрузке списка моделей в настройках. Расписаний, повторов по таймеру и фоновых очередей нет.
