# Что выяснено (факты и статус)

> См. также: [требования](../product/requirements.md) · [адаптеры](../architecture/adapters.md) · [этап 0](../stages/0-spike.md)

Исходные факты до этапа 0. После этапа 0 проверенные значения живут в `docs/backend-notes.md` (источник истины для констант).

| Тема | Что известно | Статус |
|---|---|---|
| Вход через подписку | OAuth 2.0 + PKCE (S256), issuer `https://auth.openai.com`, authorize `{issuer}/oauth/authorize`, token `{issuer}/oauth/token`, публичный client_id Codex | [факт] из исходников `openai/codex` (`codex-rs/login`) |
| Redirect | Зашит `http://localhost:1455/auth/callback`, порт фиксирован регистрацией клиента. Своя URL-схема приложения не подойдёт | [факт] |
| Параметры authorize | `scope=openid profile email offline_access` (в новых версиях + `api.connectors.*`), `id_token_add_organizations=true`, `codex_cli_simplified_flow=true`, `originator=<клиент>` | [факт], точные значения сверить с актуальным кодом |
| Device code flow | `POST {auth}/deviceauth/usercode` → `device_auth_id`, `user_code`, `interval`; опрос `POST {auth}/deviceauth/token` → `authorization_code`, `code_verifier`; затем обмен на токены. Страница ввода кода `https://auth.openai.com/device`, код живёт 15 мин | [факт] из `device_code_auth.rs` |
| Условие device code | Для личного аккаунта переключатель «Device code authorization» в Settings → Security должен быть включён | [факт] по сторонним гайдам, [проверить] |
| Токены | `id_token`, `access_token`, `refresh_token`, `account_id` (claim `chatgpt_account_id` в JWT). Обновление за ~30 с до истечения или после 401. Без обновления ~8 дней сессия протухает | [факт] частично, [проверить] |
| Эндпоинт модели | `POST https://chatgpt.com/backend-api/codex/responses`, формат Responses API, ответ — SSE | неофициальные SDK, [проверить] |
| Заголовки бэкенда | `Authorization: Bearer`, `chatgpt-account-id`, `originator`, `Accept: text/event-stream`. **Опровергнуто 22.09.2026:** `OpenAI-Beta` не нужен, чужой `originator` принят (HTTP 200), см. [backend-notes.md](../backend-notes.md) | проверено запуском |
| Тело запроса | `instructions` обязателен, `store=false` обязателен, `content.type = input_text`, `prompt_cache_key` + `session_id` = UUID для кэша | проверено запуском, см. [backend-notes.md](../backend-notes.md) |
| События SSE | `response.output_text.delta`, `response.output_item.done` (в т.ч. `function_call` с `call_id`, `name`, `arguments`), `response.completed` | [проверить] |
| Доступность | Работа бэкенда зависит от плана, workspace и ревизии бэкенда | неофициальные источники |
| Официальный путь для «богатых» клиентов | `codex app-server` (JSON-RPC, stdio/WebSocket), сторонние клиенты явно не гарантируются, WebSocket помечен как experimental | [факт] из docs |
| Будильник | `AlarmClock.ACTION_SET_ALARM`, extras `EXTRA_HOUR`, `EXTRA_MINUTES`, `EXTRA_MESSAGE`, `EXTRA_DAYS`, `EXTRA_SKIP_UI`; нужен `<uses-permission android:name="com.android.alarm.permission.SET_ALARM"/>`; перед запуском `resolveActivity()`; на Android 11+ нужен `<queries>` | [факт] developer.android.com |
| Результат будильника | Intent не возвращает подтверждение, что будильник создан | [факт] (по документации нет API результата) |
| Уведомления | Android 13+ (API 33): runtime-разрешение `POST_NOTIFICATIONS`, нужен канал | [факт] |
| Файлы (SAF) | `DocumentFile.listFiles()` медленный (много IPC-вызовов). Быстрее — прямой запрос `ContentResolver.query` по `DocumentsContract.buildChildDocumentsUriUsingTree` | [факт] |
| Хранение токена | `EncryptedSharedPreferences` (security-crypto) устарел, новых релизов нет. Замена: DataStore + шифрование ключом из Android Keystore (напрямую или через Tink) | [факт] |
