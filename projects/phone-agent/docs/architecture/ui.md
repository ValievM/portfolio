# UI

> См. также: [дизайн](../design/overview.md) · [правила вёрстки](../design/layout-rules.md) · [адаптеры](adapters.md)

- Внешний вид, токены, компоненты и правила вёрстки: **[design/overview.md](../design/overview.md)**. Здесь только архитектурная часть.
- Compose + Material 3, одна Activity, **Navigation 3** (стабильная: ключи `NavKey` + `NavDisplay`).
- Пакеты в `:app`:
  ```
  ui/
  ├─ designsystem/   theme/ (цвета, типографика, формы, токены), components/ (Composer, ToolCallCard, AssistantMarkdown…), icons/ (AppIcons)
  ├─ chat/ login/ settings/     экраны: Route, Screen, ViewModel, UiState, UiEvent
  └─ navigation/     ключи Navigation 3 и корневой NavDisplay
  ```
  `designsystem` не знает про экраны; экраны берут из него компоненты и тему. Когда второму модулю понадобится дизайн-система, она выделяется в `:core:designsystem`.
- Карточка вызова и шторка подтверждения строятся из `CallDescription` (`Tool.describe`), общего для всех инструментов.
- **Однонаправленный поток данных (UDF, «MVI-lite»):** ViewModel отдаёт `StateFlow<XUiState>` и принимает `onEvent(XUiEvent)`. Состояние неизменяемое. Разовые эффекты (например, открыть настройки) кодируем как состояние, которое UI «потребляет», а не отдельными каналами, где можно.
- На экран два composable: `XRoute` (берёт ViewModel, собирает состояние) и `XScreen(state, onEvent)` (без логики, легко превьюить и тестировать).
- `ChatUiState`: список `ChatMessage(id, role: User|Assistant|Tool, text, toolCall?)` — вместо трёх типов сообщений одна модель с ролью; карточка инструмента живёт в поле `toolCall` (статус, описание, аргументы, fileHits). Поля `streaming` нет: идущий ответ узнаётся по состоянию хода, текст растёт дельтами. Ход описывает `turn: TurnState` (sealed, `:core:domain`): `Idle`, `Running`, `AwaitingApproval` (с ожидаемым `CallId` или пачкой в исходном порядке), `Stopped`, `Interrupted`, `FailedRetryable`, `FailedTerminal`; переходы делает одна чистая функция `transitionTurn(state, event)`, ViewModel только применяет её, невозможные сочетания флагов непредставимы. Повтор доступен из `Interrupted` и `FailedRetryable` (`hasRetryAction()`), поле ввода занято при `Running` и `AwaitingApproval` (`isInProgress()`). Ошибки загрузки и сохранения истории лежат отдельно в `historySync` и не меняют состояние хода; при показе ошибка хранилища идёт раньше ошибки хода (`errorForDisplay()`). Черновик ввода в `ChatScreen` (`rememberSaveable`), не в состоянии экрана. Диалоги подтверждения приходят от `UserInteractionBridge`.
- ViewModel собирает `Flow<AgentEvent>` в `ChatUiState`. Логики выполнения и безопасности во ViewModel нет.
- Тексты ошибок (`AgentError` → строка) — только здесь.
- ViewModel не держит `Context` и `Activity`.
