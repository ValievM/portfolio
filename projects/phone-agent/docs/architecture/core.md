# Ядро (`:core:domain`)

> См. также: [обзор](overview.md) · [адаптеры](adapters.md) · [тесты](testing.md)

## Структура пакетов
```
com.example.phoneagent.domain
├── agent/         AgentLoop, AgentEvent, AgentConfig, AgentError
├── conversation/  HistoryItem, ToolCall, ContextWindowPolicy
├── model/         ModelGateway (порт), ModelRequest, ModelEvent, ModelCatalog (порт)
├── tools/         Tool, ToolSpec, Risk, ToolRegistry, ToolPipeline, ToolOutcome
├── auth/          CredentialsProvider, Credentials, ApiKeyValidationGateway (порты)
├── interaction/   UserInteraction (порт), ApprovalRequest
├── storage/       ConversationRepository, SettingsRepository (порты), AppSettings
└── platform/      Clock, DispatcherProvider, IdGenerator, AppLogger, LogRedaction
```
Разрешённые библиотеки: kotlin-stdlib, `kotlinx-coroutines-core`, `kotlinx-serialization-json`. Запрещены: `android.*`, `androidx.*`, OkHttp, Room, Dagger/Hilt, Compose.

## Ключевые порты (эскиз)
```kotlin
public interface ModelGateway {
    public fun stream(request: ModelRequest): Flow<ModelEvent>        // ошибки приходят как ModelEvent.Failed
}
public sealed interface ModelEvent {
    data class TextDelta(val text: String) : ModelEvent
    data class ToolCallRequested(val call: ToolCall) : ModelEvent
    data class OpaqueItem(val provider: String, val payload: JsonElement) : ModelEvent  // см. «История и непрозрачные элементы» ниже
    data class Completed(val usage: Usage?) : ModelEvent
    data class Failed(val error: AgentError) : ModelEvent
}

public interface Tool {
    public val spec: ToolSpec                     // имя, описание для модели, JSON-схема, Risk
    public fun describe(args: JsonObject): CallDescription   // человеческое описание вызова: заголовок, суть, детали
    public suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult
}
// CallDescription(title, summary, details) нужен для карточки в ленте и текста подтверждения (ApprovalRequest = CallDescription + Risk).
// Его формирует сам инструмент (строки из ресурсов :tools), поэтому UI не знает про конкретные инструменты
// и подтверждение рисуется из структурированных данных, а не из текста модели.

public interface UserInteraction {
    public suspend fun confirm(request: ApprovalRequest): Boolean
    public suspend fun confirmBatch(requests: List<ApprovalRequest>): Boolean   // один вопрос на пачку вызовов одного ответа (ADR 0004)
    public suspend fun ensure(capability: Capability): CapabilityResult   // Capability: Notifications и т.п.
}

public interface CredentialsProvider {        // то, что нужно клиенту модели от слоя входа
    public suspend fun current(): CredentialsResult // Ready(токен + accountId либо API-ключ), SignedOut или Unavailable (временный сбой обновления, сессия не потеряна)
    public suspend fun refreshAfterUnauthorized(): CredentialsResult
}
```
`Capability` — понятие ядра («нужны уведомления»). В Android-разрешения его переводит `:app`, ядро про них не знает.

## Цикл агента (`AgentLoop`) — императивный Flow

`AgentLoop.run(history, input): Flow<AgentEvent>` — **холодный Flow**, не привязан ни к ViewModel, ни к Android. Его можно позже запустить из сервиса или фоновой задачи без переписывания. Внутри — императивный `flow { while (true) }` по шагам модели; автомата с `reduce(state, event)` нет, поведение то же, что задумывалось:

- шаг: запрос модели → текст и вызовы инструментов; шагов не больше `maxSteps`, иначе `TurnFailed(StepLimit)`;
- вызовов инструментов за ход не больше `maxToolCallsPerTurn` (30, не меньше 1): шаг, который не помещается в остаток, отклоняется целиком (ни один вызов в нём не запускается), ход завершается `TurnFailed(StepLimit)`, сделанное остаётся в истории;
- `Tool.describe` упал: для `SAFE` карточка нейтральная (название = имя инструмента) и вызов идёт дальше; для `CONFIRM`/`SENSITIVE` вызов не запускается и шторка не показывается (`Failed`), описание запрашивается один раз и показывается ровно то же; в пакете битый участник отклоняет всю группу (модель получит «пользователь отказал», хотя ему ничего не показывали: известная неточность);
- ошибка запроса → `TurnFailed(error)`; ответ без вызовов → `TurnCompleted(newItems)`;
- отмена собирающего — обычная отмена корутины: сеть и инструмент прерываются, `CancellationException` не глотается.

- Ответ модели может содержать **несколько вызовов**: выполняем по порядку, все результаты добавляем в историю, только потом следующий запрос. Параллелить можно только `SAFE`-инструменты, и не в MVP.
- Карточки инструментов переживают перезапуск: вызов и результат лежат в истории (`ToolCallItem` + `ToolOutput`). Результат сохраняется текстом `ToolOutcome.toOutputText()` (успех как есть, `Rejected: …`, `Failed: …`); чат читает его обратно через `toolOutcomeFromOutputText`, сопоставляет по `callId`, а описание берёт у `AgentLoop.describe(call)`. Успешный `ToolOutcome` может нести дополнительный `ToolContent`: он передаётся из `ToolResult` в событии, но не сериализуется в историю и не уходит модели отдельно. При восстановлении `AgentLoop` просит зарегистрированный инструмент восстановить своё структурированное содержимое из старого текста; UI не знает имён инструментов и не разбирает их JSON. Поэтому формат `ToolOutput` и схема Room не меняются. Вызов без результата (приложение убили) рисуется как «Прервано». Ограничение текстового статуса остаётся: успех, текст которого начинается с метки `Rejected: ` или `Failed: `, прочитается как ошибка.
- Тесты цикла — на `AgentLoop` с фейковым `ModelGateway`, без сети; логика шагов и лимитов та же, что задумывалась для автомата.
- `AgentEvent` (для UI): `TextDelta`, `ToolCallStarted`, `ApprovalRequested`, `ToolCallFinished`, `TurnCompleted(newItems)`, `TurnFailed(error)`.
- История между запросами передаётся целиком (`store=false`); сколько отправлять, решает `ContextWindowPolicy` (стратегия, заменяемая: «последние N», «по размеру», позже «с суммаризацией»).

## Инструменты: плагины + конвейер

**Регистрация.** `ToolRegistry` собирается из `Set<Tool>`, который даёт DI (Hilt multibinding). Цикл агента про конкретные инструменты не знает.

**Конвейер.** Один класс `ToolPipeline`: имя ищется в реестре, дальше — подтверждение по `Risk`, таймаут, вызов инструмента, обрезка вывода до 4 КБ, лог без секретов. Инструмент этого не реализует. Готовых ступеней-«миддлварей» нет — порядок зашит в `ToolPipeline`, расширение новым шагом означает правку этого класса.

- Обязательные аргументы (`required` в схеме) проверяет `ToolPipeline`; остальную валидацию (диапазоны, лимиты длин) делают сами инструменты через sealed-результаты валидации.
- Разрешения: инструмент объявляет `ToolSpec.requiredCapabilities`, `ToolPipeline` запрашивает их **до** таймаута (время на системный диалог не считается); отказ — `Failed` с текстом `Tool.deniedMessage`. Исключение из `Tool.execute` (кроме отмены) превращается в `Failed("tool failed: <тип>")`, а не обрывает ход.
- `ApprovalPolicy` по `Risk` инструмента: `SAFE` (без вопроса: `find_files`, `notify`), `CONFIRM` (разовое подтверждение: `set_alarm`; несколько `CONFIRM`-вызовов одного ответа модели подтверждаются одним вопросом «всё или ничего», до 15 за раз: `ToolPipeline.batchGroups` и `confirmBatch`, [ADR 0004](../adr/0004-batch-approval.md)), `SENSITIVE` (подтверждение с деталями, «запомнить» запрещено; на будущее: SMS, звонки).
- Результат: `ToolOutcome = Success(text, content?) | Rejected(reason) | Failed(error)`. Это итог всего конвейера, включая отказ пользователя и таймаут. `Rejected` с причиной `user declined` означает отказ пользователя; у пачки, которую не показали из-за сломанного описания действия, причина сообщает, что подтверждения не было и вызовы не запускались. `ToolResult` — только то, что вернул сам инструмент. Дополнительный `ToolContent` — типизированные метаданные для клиентов, не замена и не изменение текста для модели. Любой исход превращается в `function_call_output` для модели.

Так безопасность, лимиты и логирование живут в одном месте и применяются ко **всем** инструментам, включая будущие.

**Типизированные аргументы.** Каждый инструмент сам описывает JSON-схему (`buildJsonObject`) и декодирует аргументы в свой `@Serializable`-класс (`AlarmArgs`, `NotifyArgs`, …); декодирование падает в `ToolOutcome.Failed`, не в исключение. Общего `TypedTool<A>` и DSL схем нет — при 3–5 инструментах это было бы лишним слоем; заводим, когда инструментов станет заметно больше.

## История и «непрозрачные» элементы
История — не только текст. Модель со `store=false` может возвращать служебные элементы (например, зашифрованные reasoning-блоки), которые нужно вернуть в следующем запросе. Поэтому `HistoryItem` включает `Opaque(provider, payload)`: ядро их не понимает, но хранит и отдаёт обратно адаптеру. Нужно ли это для нашего бэкенда — проверяется на этапе 0 (пункт 0.3). Закладываем слот сейчас, чтобы не менять формат хранения потом.

## Ошибки
```
AgentError
├─ SessionExpired · Forbidden · Offline · Timeout
├─ RateLimited(retryAfter?) · ProtocolChanged · Server(code)
├─ ToolFailure(reason) · StepLimit · Interrupted
└─ Cancelled · Unexpected(details)
```
Плоский список без групп-обёрток (не иерархия `Auth`/`Network`/`Tool`):sealed-типов меньше, а исчерпывающий `when` в UI всё равно заставляет обработать каждый. Отдельных `Validation`/`PermissionDenied` нет: отказ валидации приходит как `ToolFailure`, а «запрещено» — это `Forbidden` или исход `ToolOutcome.Rejected` (отказ пользователя либо вызов без показа подтверждения), отдельного кода не нужно.
В ядре ошибки без текста для людей. Тексты на русском, `strings.xml`, подставляет UI-слой. `CancellationException` всегда пробрасывается дальше и никогда не глотается.
