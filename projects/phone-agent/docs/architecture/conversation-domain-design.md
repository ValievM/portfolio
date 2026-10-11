# Эпик «Домен разговора»: проектирование

> Статус 2026-09-25: T8 и T9 реализованы, T7 отложен (см. ADR 0005).

Решение: [ADR 0005](../adr/0005-conversation-domain.md). Порядок: T8 → T9 → T7. Здесь только документы.

## Текущее состояние (main 9f04c86)

- ChatViewModel владеет history/Job/historyBeforeTurn/pendingOwnEmissions и CallId→карточка (ChatViewModel.kt:36–63); observe игнорирует свои echo, но отменяет ход на внешней эмиссии (66–100). Submit сначала сохраняет provisional-вопрос; Retry отбрасывает попытку (130–220, 260–277).
- ChatUiState допускает независимые messages/isRunning/error/canRetry (ChatContract.kt:9–15): тип разрешает все 8 комбинаций этих осей. Ошибка сохранения тоже попадает в error; TurnFailed выставляет error/retry до finally, поэтому возможно «ход идёт + ошибка» (ChatViewModel.kt:49–53, 180–220). Цель: активный Job+Failed/Retry и ошибка Room внутри turn-state не представимы.
- При старте trailing UserMessage даёт Interrupted+Retry; пустая эмиссия очищает UI (ChatStateTransforms.kt:42–64). ProcessDeathTest покрывает save/Stop/restart/ID (53–135, 151–267); OrderTest — User→текст→карточка→финальный текст (52–79).
- ToolCall.id фактически call_id, itemId — provider item id; HistoryItem снова называет item id просто id (HistoryItem.kt:6–45, ResponsesEventMapper.kt:48–56, AgentLoop.kt:58–60).
- Room v1 — одна глобальная таблица с PK=position; DAO целиком читает/наблюдает/удаляет её, replace транзакционный (ConversationEntity.kt:6–19, ConversationDao.kt:10–31, RoomConversationRepository.kt:9–20). Порт без ConversationId (Repositories.kt:6–12); AppDatabase v1, exportSchema=false, migration нет (AppDatabase.kt:6–8, ConversationStorageModule.kt:25–35).

### Batch approval (ветка feat/batch-approval, не main)

ADR 0004 / feat/batch-approval: валидные CONFIRM группируются по 2–15; SAFE/SENSITIVE/invalid/singleton вне пачки. Отказ/отмена не исполняет; карточки и ordered CallDescription публикуются до запуска. Проверено по коду/тестам: ToolPipeline.kt:57–81, AgentLoop.kt:59–127, AgentLoopBatchApprovalTest.kt:46–165, BatchApprovalSheet.kt:36–55, InteractionHost.kt:24–47.

parallel_tool_calls=true проверен на живом бэкенде 2026-09-24 (пачка из 3-4 вызовов принята), пачки подтверждений уже в main.

## Целевые типы и инварианты

### T8 — ID

Обёртки без префиксной эвристики; API/Room boundary — String. Удалить fallback itemId=id.

~~~kotlin
@JvmInline value class CallId(val value: String) // call_id
@JvmInline value class ItemId(val value: String) // provider/history item id
@JvmInline value class ConversationId(val value: String) {
    companion object { val DEFAULT = ConversationId("default") }
}

sealed interface HistoryItem { val itemId: ItemId }
data class ToolCall(val callId: CallId, val itemId: ItemId, val name: String, val arguments: JsonObject)
data class ToolCallItem(override val itemId: ItemId, val callId: CallId, val name: String, val arguments: JsonObject) : HistoryItem
data class ToolOutput(override val itemId: ItemId, val callId: CallId, val output: String) : HistoryItem
fun interface IdGenerator { fun newItemId(): ItemId }
~~~

HistoryItem/Opaque и UI key — ItemId; вызовы/outputs/events — CallId. DTO/Room остаются String. Пустой wire-ID — [проверить].

### T9 — sealed-состояние

TurnState/PendingApproval живут в :core:domain; snapshot/HistorySyncState — в :app. Они заменяют флаги; sync БД отделён.

~~~kotlin
sealed interface TurnState {
    data object Idle : TurnState
    data class Running(val questionId: ItemId) : TurnState
    data class AwaitingApproval(val questionId: ItemId, val pending: PendingApproval) : TurnState
    data class Stopped(val questionId: ItemId) : TurnState
    data class Interrupted(val questionId: ItemId) : TurnState
    data class FailedRetryable(val questionId: ItemId, val error: AgentError) : TurnState
    data class FailedTerminal(val error: AgentError) : TurnState
}
sealed interface PendingApproval {
    data class Single(val callId: CallId, val request: ApprovalRequest) : PendingApproval
    data class Batch(val calls: List<ApprovedCall>) : PendingApproval
}
data class ApprovedCall(val callId: CallId, val request: ApprovalRequest)
sealed interface HistorySyncState {
    data object Loading : HistorySyncState
    data object Ready : HistorySyncState
    data class Failed(val operation: StorageOperation) : HistorySyncState
}
enum class StorageOperation { LOAD, SAVE }
data class ConversationSnapshot(
    val id: ConversationId, val history: List<HistoryItem>,
    val turn: TurnState, val sync: HistorySyncState,
)
~~~

Stop сохраняет вопрос без Retry; новый Submit продолжает историю. На старте trailing UserMessage даёт Interrupted+Retry. TurnState не персистится.

AgentEvent.ApprovalRequested должен нести CallId; BatchApprovalRequested — List<CallId>. Так VM не ищет «последнюю карточку» по порядку событий.

~~~mermaid
stateDiagram-v2
    [*] --> Idle
    Idle --> Running: Submit; сохранить вопрос первым
    Running --> AwaitingApproval: single / batch
    AwaitingApproval --> Running: approve exact set
    AwaitingApproval --> Running: decline; calls Rejected
    Running --> Idle: Completed + persist
    Running --> FailedRetryable: retryable failure
    Running --> FailedTerminal: terminal failure
    Running --> Stopped: Stop; cancel, вопрос оставить
    Stopped --> Running: новый Submit
    Idle --> Interrupted: startup + trailing UserMessage
    Interrupted --> Running: Retry, заменить незавершённую попытку
    FailedRetryable --> Running: Retry
    Running --> Idle: внешняя очистка; cancel + join + принять пустое
    AwaitingApproval --> Idle: Stop / clear / host dispose
~~~

Инварианты: Idle не исполняет; AwaitingApproval хранит точный Single/Batch; Retry только из Interrupted/FailedRetryable; ошибка Room не ошибка модели. Approval не переживает отмену/death.

## T7 — Session, Repository и Room

### Где живёт ConversationSession

Выбор — internal координатор :app, без Android/Compose. Session владеет history, Job, retry snapshot и Room emissions; ChatViewModel — только UI-проекция/команды. Chat/Settings делят DEFAULT; очистка через Session.

| :core:domain | :app — выбрано |
|---|---|
| Чистые ID/state/переходы уместны; Room, Job и запись — application orchestration. | Уже соединяет DI/Room/UI/domain. Session зависит от domain-портов, тестируется без устройства, исполняется в Job caller без GlobalScope. |

В строгом DDD Session — application coordinator, не богатый aggregate. Новый модуль — только при втором потребителе.

~~~kotlin
interface ConversationRepository {
    suspend fun load(id: ConversationId = ConversationId.DEFAULT): List<HistoryItem>
    suspend fun replace(items: List<HistoryItem>, id: ConversationId = ConversationId.DEFAULT)
    fun observe(id: ConversationId = ConversationId.DEFAULT): Flow<List<HistoryItem>>
}
~~~

replace атомарно меняет один ID; position от 0. Default сохраняет Kotlin-вызовы, но фейки/реализации обновить. DAO фильтрует по ID, PK=(conversationId,position).

~~~kotlin
interface ConversationSession {
    val state: StateFlow<ConversationSnapshot>
    suspend fun submit(text: String, onEvent: suspend (AgentEvent) -> Unit)
    fun stop()
    suspend fun retry(onEvent: suspend (AgentEvent) -> Unit)
    suspend fun clearHistory()
}
~~~

submit сохраняет вопрос до AgentLoop. stop отменяет Job; clearHistory запрещает Submit, cancel+join, затем очищает. Собственные echo игнорируются; чужое изменение отменяет ход. Записи сериализованы.

~~~mermaid
flowchart LR
  ChatVM --> Session[Session :app]
  SettingsVM -->|clear| Session
  Session --> Domain[:core:domain / AgentLoop]
  Session --> Repo[Repository + ConversationId]
  Repo --> Room[Room]
~~~

[проверить] Тестом подтвердить одинаковые/быстрые Room emissions. Если value-based echo неоднозначен, добавить revision token в порт.

### Миграция Room v1 → v2 и откат

1. До правки Entity включить exportSchema и сохранить v1 (сейчас schema нет).
2. Пересоздать таблицу с прежними колонками + conversationId NOT NULL, PK=(conversationId,position); старые строки скопировать с ID default. ALTER одной колонки не сменит PK.
3. Явная Migration(1,2), scoped DAO/replace, без destructive fallback.
4. Тест старой БД проверяет перенос User/ToolCall/ToolOutput/Opaque, порядок/JSON; контрактный тест — два ID, одинаковые позиции, очистка одного.

Правило схемы: каждое повышение `AppDatabase.version` коммитит экспортированный JSON новой версии, явную миграцию с предыдущей версии и тест, который проверяет перенос данных. `fallbackToDestructiveMigration` запрещён.

Есть Room runtime/Robolectric, нет room-testing. Сначала SQL fixture; MigrationTestHelper требует новой зависимости. Не запускалось: [проверить].

Downgrade 2→1 разрешать лишь для ID default; иначе завершать ошибкой без потери данных. Тестировать оба исхода; destructive downgrade запрещён. После появления нескольких чатов — только forward-fix.

## План реализации: 8 коммитов

Для каждого: тест первым, Red по поведению → минимальный Green → Refactor.

| № | Красный тест первым | Код после него / зелёное |
|---|---|---|
| 1 T8 domain | AgentLoop разделяет call/item IDs | HistoryItem, ID types, PlatformPorts, AgentLoop; core tests |
| 2 T8 wire | SSE IDs round-trip в разные JSON поля | ResponsesEventMapper/RequestBuilder; :data:llm:test |
| 3 T8 app | ID и Opaque Room/UI round-trip | Chat UI, mapper; mapper/order/process tests |
| 4 T9 | Все переходы/невозможные комбинации | TurnState/reducer; pure tests |
| 5 T9 UI | Single/batch approve/reject/cancel; sync отдельно | Chat reducer/VM; Compose + batch tests |
| 6 T7 Session | Save-before-run, Stop, Retry once, own echo, clear | Session + fake-port/process/order tests |
| 7 T7 Room | v1→v2/backfill, isolation, scoped clear, downgrade | Port/Entity/DAO/repo/DB, app/build.gradle.kts/schema + migration tests |
| 8 T7 wiring | Chat/Settings share session; clear in flight; restart | ChatVM/SettingsVM/Hilt; app tests + ./scripts/check.sh full |

## Поведения-контракты и будущие функции

1. Порядок текста/карточек; Stop отменяет сеть/инструмент, сохранив вопрос.
2. Retry ровно один раз повторяет незавершённый вопрос и сохраняет старые ходы.
3. Process death не восстанавливает Job/approval; trailing UserMessage → Interrupted; Retry только явно.
4. Свой Room echo не отменяет ход; чужая эмиссия отменяет его до принятия.
5. Question ItemId уникален; CallId ≠ ItemId на пути Responses→AgentLoop→Room→запрос.
6. Batch: только показанные CONFIRM (≤15); отказ/отмена не исполняет; SENSITIVE индивидуален; порядок вызовов сохранён.
7. Очистка scoped по ID; Opaque сохраняет provider/payload/id.

Несколько чатов: Session на ConversationId, ID приходит из навигации. Headless runner в app переиспользует Session; AgentLoop уже UI-free. Голос подаёт текст тем же Submit, audio/TTS — адаптер.

Топ-риски: повтор побочного эффекта после crash и Retry; гонка own echo/очистки/параллельной записи; schema v1 отсутствует, downgrade нужно оградить.

Вопросы владельцу: сохраняем Stop без немедленного Retry? Блокируем downgrade с non-default history или нужен экспорт? DismissError забывает Retry или лишь скрывает текст? Принимаем повтор внешнего эффекта после crash или нужны idempotency keys?
