# Адаптеры

> См. также: [ядро](core.md) · [UI](ui.md) · [требования](../product/requirements.md)

## `:data:llm` — клиент модели (anti-corruption layer)
```
ResponsesGateway (public, реализует ModelGateway)
├─ BackendProfile        только URL и системный промпт; один профиль ChatGptSubscription
├─ request/              сборка запроса из ModelRequest, включая выбор модели (internal)
├─ dto/                  DTO формата Responses API (internal, никогда не выходят за модуль)
└─ sse/                  разбор потока: парсер кадров и маппинг в ModelEvent (internal)
```
- Профиль один: клиент работает только с подпиской. Заголовки (токен, `chatgpt-account-id`, версия клиента) и имя модели подставляет `ResponsesGateway` при сборке запроса. Слота под API-профиль нет — если появится API-вход, профиль и заголовки вернутся в `BackendProfile`.
- OkHttp напрямую, без Retrofit: один эндпоинт и SSE, Retrofit почти ничего не даёт.
- Ретрай 401 — не OkHttp `Authenticator`, а обычный suspend-цикл в `ResponsesGateway` (не больше двух попыток): после сбоя вызывается `CredentialsProvider.refreshAfterUnauthorized()` и запрос собирается заново. Результат различает `Ready`, `SignedOut` (сессия потеряна: «Сессия завершилась») и `Unavailable` (обновить сейчас нельзя, например нет сети: ошибка сети с «Повторить», токен цел). Причина: `Authenticator` требует `runBlocking` на suspend-обновлении, что правилами запрещено; single-flight обновления токена живёт в `:data:auth`.
- Если бэкенд поменял формат: правим `dto/` и `sse/` + обновляем записанный образец ответа в тестах. Остальной код не трогаем.

## `:data:auth`
```
AuthProvider (стратегия):  ChatGptBrowserAuth · ChatGptDeviceCodeAuth · ApiKeyAuth
SessionManager             хранит AuthState (StateFlow), реализует CredentialsProvider
TokenStore                 DataStore + AES-GCM, ключ в Android Keystore
TokenRefresher             мьютекс, атомарная запись нового refresh-токена
pkce/ · jwt/               чистые функции (тестируются без Android)
AuthConfig                 issuer, client_id, scope, redirect, параметры (значения из docs/backend-notes.md)
```
**Порт 1455 может занять чужое приложение.** Украденный `code` бесполезен без `code_verifier` (PKCE), `state` проверяется обязательно, при занятом порту включается device code.

Провайдеры собираются явным `mapOf` в `SessionAssemblyModule` (`app/di`), без Hilt `@IntoMap`: способов входа три и состав стабильный, явная карта виднее на ревью. Добавить способ входа = новый класс + строка в этой карте + кнопка в UI; при появлении динамики переходим на multibinding.

## `:tools`
```
tools/
├─ alarm/    AlarmTool, AlarmArgs, AlarmIntentFactory (чистая функция), AlarmBindings
├─ notify/   NotifyTool, NotifyArgs, Notifier, NotifyBindings
└─ files/    FindFilesTool и SearchFilesTool (ADR 0003), их Args, SAF-обход, SelectedFolder, FilesBindings
```
- Пакет `common/` не понадобился — общих помощников пока нет. Файловых инструментов два: `find_files` по выбранной SAF-папке и `search_files` по глобальному MediaStore (ADR 0003). Всё `internal`, наружу торчит только регистрация; пакеты инструментов **не импортируют друг друга** (проверяет тест).
- **Humble object.** Вызовы Android API вынесены в тонкие классы (`Notifier`, запуск intent, `SafTreeWalker`), логика (сборка intent, валидация, лимиты обхода) — в чистые функции, которые тестируются без устройства.
- Папку для поиска пользователь выбирает **заранее в настройках** (хранится в `SettingsRepository`). Если не выбрана, `find_files` возвращает `Failed(NeedsSetup)`, и UI предлагает открыть настройки. Так инструменту не нужен Activity-результат в момент работы.

## `UserInteractionBridge` (`:app`) — самое хитрое место
Инструментам и циклу нужно спросить пользователя (подтверждение, разрешение), а они работают вне UI. Решение — мост:
1. Ядро вызывает `suspend fun confirm(request)`.
2. Мост кладёт запрос в `StateFlow<PendingInteraction?>` и ждёт `CompletableDeferred`.
3. Корневой composable (`InteractionHost`) показывает шторку подтверждения или запускает системный запрос разрешения (`rememberLauncherForActivityResult`), завершает `deferred`.
4. Если экран ушёл или пользователь отменил: `false` / `Denied`, ожидание отменяется вместе с корутиной.

Ядро и инструменты ничего не знают про Activity и Compose.

## Хранилище (в `:app`, пока)
- Room: одна таблица `conversation_history` (`ConversationEntity`), JSON для `Opaque`-элементов. **Entity отдельно от доменных моделей**, между ними мапперы. `exportSchema = false`, версия 1, миграций пока нет — они появятся вместе с первым изменением схемы.
- DataStore: `AppSettings` (модель, папка поиска, включённые инструменты).
- Порты `ConversationRepository`, `SettingsRepository` определены в ядре; `conversationId` в API не появился — история одна, несколько диалогов не заложены ни в API, ни в таблицу. Если понадобятся, это миграция + расширение порта.
