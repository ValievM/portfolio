# Заметки о входе и бэкенде (результат этапа 0)

> См. также: [research/backend-facts.md](research/backend-facts.md) (исходные гипотезы) · [architecture/adapters.md](architecture/adapters.md)· [adr/0002-subscription-login-go.md](adr/0002-subscription-login-go.md)

**Источник истины для констант входа и бэкенда.** Проверено 21-22.09.2026 скриптом `spike/probe.py` на аккаунте с тарифом Plus. Источник исходных значений: репозиторий `openai/codex`, коммит `142360d` (21.09.2026). Статус у каждого значения: **запуск** (проверено живым запросом), **исходники** (из кода Codex, не проверено), **не проверено**.

## Вердикт: GO
Вход через подписку работает: полный цикл (вход, обновление токена, список моделей, запрос с вызовом инструмента и ответ) прошёл. Основной способ входа в приложении: подписка. `ApiKeyAuth` остаётся обязательным запасным (бэкенд закрытый, стабильность не гарантирована).

## Вход (OAuth 2.0 + PKCE)
| Что | Значение | Статус |
|---|---|---|
| issuer | `https://auth.openai.com` | запуск |
| authorize | `GET {issuer}/oauth/authorize` | запуск |
| token | `POST {issuer}/oauth/token`, `application/x-www-form-urlencoded` | запуск |
| client_id | `app_EMoamEEZ73f0CkXaXp7hrann` (публичный клиент Codex) | запуск |
| redirect_uri | `http://localhost:1455/auth/callback` (порт фиксирован регистрацией клиента) | запуск на Mac, на Android не проверено |
| scope | `openid profile email offline_access api.connectors.read api.connectors.invoke` | запуск |
| Параметры authorize | `response_type=code`, `code_challenge_method=S256`, `state`, `id_token_add_organizations=true`, `codex_cli_simplified_flow=true`, `originator=codex_cli_rs` | запуск (без дополнительных не проверяли) |
| Обмен кода | `grant_type=authorization_code`, `client_id`, `code`, `redirect_uri`, `code_verifier`. Ответ: `id_token`, `access_token`, `refresh_token` | запуск |
| Обновление | `grant_type=refresh_token`, `client_id`, `refresh_token`. Ответ: `access_token`, `id_token`, `refresh_token`, `expires_in`, `earliest_refresh_at`, `scope`, `token_type` | запуск |
| Ротация | **refresh_token меняется при каждом обновлении** (новый выдаётся, старый заменять) | запуск |
| Срок access-токена | около 10 дней (≈864000 с) | запуск |
| Claims | `https://api.openai.com/auth` → `chatgpt_account_id`, `chatgpt_plan_type` (в `id_token`); `exp` в `access_token` | запуск |
| Device code | `POST {auth}/deviceauth/usercode`, опрос `{auth}/deviceauth/token`, страница `https://auth.openai.com/device`, код живёт 15 мин | исходники |

Замечание: в ответе на обновление есть `earliest_refresh_at`. Не обновлять токен раньше этого момента; уточнить поведение на этапе 2.

## Бэкенд модели
| Что | Значение | Статус |
|---|---|---|
| Base URL | `https://chatgpt.com/backend-api/codex` | запуск |
| Запрос модели | `POST {base}/responses`, ответ SSE | запуск |
| Список моделей | `GET {base}/models?client_version=<версия>` | запуск |
| Заголовки (отправлены, сработали) | `Authorization: Bearer <access_token>`, `ChatGPT-Account-ID`, `originator`, `User-Agent`, `version`, `Accept: text/event-stream`, `Content-Type: application/json`, `session-id`, `x-client-request-id` | запуск; какие обязательны, не выяснено |
| `originator` | значение `phone_agent` вместо `codex_cli_rs` принято (HTTP 200); белого списка не обнаружено. Может измениться | запуск |
| `OpenAI-Beta` | не отправляли, не нужен для HTTP SSE (в исходниках только для websocket) | запуск |
| Content-Type ответа | **отсутствует** у SSE-ответа; парсер не должен на него опираться | запуск |
| Модели аккаунта (Plus) на момент проверки | `gpt-6-astra`, `gpt-reserve`, `gpt-5.6-sol`, `gpt-5.6-terra`, `gpt-5.6-luna`, `gpt-5.5`, `codex-auto-review`. Тест шёл на `gpt-5.6-sol`. Список брать из `/models`, не хардкодить | запуск |

## Формат запроса (принят бэкендом)
```json
{
  "model": "gpt-5.6-sol",
  "instructions": "…непустой системный промпт…",
  "input": [
    {"type": "message", "role": "user", "content": [{"type": "input_text", "text": "…"}]}
  ],
  "tools": [{"type": "function", "name": "get_local_time", "description": "…",
             "parameters": {"type": "object", "properties": {"city": {"type": "string"}},
                            "required": ["city"], "additionalProperties": false},
             "strict": false}],
  "tool_choice": "auto",
  "parallel_tool_calls": true,
  "reasoning": {"effort": "low"},
  "store": false,
  "stream": true,
  "include": ["reasoning.encrypted_content"],
  "prompt_cache_key": "<uuid сессии>"
}
```
Статус: запуск. `store=false` работает; история передаётся целиком.

**`parallel_tool_calls`: `true` (изменено, ADR 0004).** В спайке стояло `false`, поэтому модель просила по одному вызову за ответ. Для пакетного подтверждения (несколько будильников одним вопросом) нужно `true`. `spike/probe.py` по-прежнему шлёт `false` — это снимок спайка. **Проверено на живом бэкенде 2026-09-24:** запрос с `true` принят, модель вернула 3-4 вызова за один ответ. Если бэкенд когда-нибудь начнёт отвергать флаг, вернуть `false`: приложение продолжит работать по одному вызову за шаг.

## Цикл с инструментом (проверен)
1. Ход 1: в потоке приходит элемент `function_call` (`name`, `arguments` — JSON-строка, `call_id`). Финальный элемент брать из события `response.output_item.done`.
2. Ход 2: `input` = исходное сообщение + элемент `function_call` **как есть** (с `id`) + `{"type": "function_call_output", "call_id": "<тот же>", "output": "<строка>"}`. Вырезать `id` не потребовалось.
3. Ответ: `response.output_text.delta` с текстом, затем `response.completed`.

События SSE, которые наблюдались: `response.created`, `response.in_progress`, `response.output_item.added`, `response.function_call_arguments.delta` / `.done`, `response.output_item.done`, `response.content_part.added` / `.done`, `response.output_text.delta` / `.done`, `response.completed`. В `delta` есть поле `obfuscation`, игнорировать. Образец потока: `spike/sample_sse.txt` (17 КБ, без токенов), используется в тестах парсера на этапе 3.

## Не проверено (остаётся на следующие этапы)
- Будильник и приложение «Часы» (пункт 0.6).
- Device code flow.
- Минимальный набор заголовков, поведение при 401, 403, 429, лимиты подписки.
- Формат SSE-события об ошибке внутри потока (`event: error`, поле `error.message`) — в `spike/sample_sse.txt` такого события нет, обработчик в `ResponsesEventMapper` (`:data:llm`) сделан по догадке (обычный формат Responses API), не проверен живым запросом. Если реальный формат другой — увидим `ProtocolChanged` вместо понятного сообщения.
- Нужно ли возвращать reasoning-блоки при `store=false`: на простом запросе (`effort` low и high) модель их не вернула, вопрос остался открытым. Слот `HistoryItem.Opaque` сохраняем: Codex всегда просит `include: ["reasoning.encrypted_content"]`.

## Что изменилось относительно прежних документов
- `originator` не проверялся по белому списку (раньше считалось, что чужое значение даёт 403).
- `OpenAI-Beta: responses=experimental` не нужен; заголовки сессии называются `session-id` и `thread-id`, есть `version` и `x-client-request-id`.
- Access-токен живёт около 10 дней; refresh-токен ротируется (подтверждено).
- У SSE-ответа нет `Content-Type`.
