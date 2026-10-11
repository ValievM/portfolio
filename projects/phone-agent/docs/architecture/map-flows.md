# Карта архитектуры: потоки выполнения

> Снимок 2026-09-24: `файл:строка` устарели (T8, T9, «Стык»). Верить коду и [overview](overview.md).

> Карта: [обзор](map-overview.md) · [ядро](map-core-data.md) · [вход](map-auth.md) · [tools и UI](map-tools-app.md) · [app](map-app-infra.md) · **потоки**. Только то, что есть в `main`; пакетного подтверждения нет.

## Потоки и диспетчеры

| Что | Где выполняется | Откуда |
|---|---|---|
| `viewModelScope` трёх ViewModel, сбор `AgentLoop.run`, `ToolPipeline`, `Tool.execute` | Main.immediate (диспетчер коллектора) | `ChatViewModel.kt:146-154` |
| HTTP модели, каталог, ключ, token endpoint, device code, `accept()` loopback | `withContext(Dispatchers.IO)` | `ResponsesGateway.kt:125`, `OkHttpModelCatalog.kt:32`, `OkHttpTokenRefreshGateway.kt:55`, `ChatGptBrowserAuth.kt:101` |
| MediaStore (`search_files`) | `Dispatchers.IO` | `SearchFilesTool.kt:93` |
| SAF-обход (`find_files`) | без переключения, то есть Main | `FindFilesTool.kt:87` |
| `SessionAuthCoordinator.state` | `applicationScope` = Main.immediate | `AuthBindingsModule.kt:98` |
| Room, DataStore | собственные исполнители библиотек | `RoomConversationRepository`, `PreferencesSettingsRepository` |
| Запись лога | поток вызывающего, синхронно | `AndroidLogcatLogger.kt:61-66` |

## (а) Один ход чата

```mermaid
sequenceDiagram
autonumber
participant UI as ChatScreen
participant VM as ChatViewModel
participant Repo as ConversationRepository
participant AL as AgentLoop
participant GW as ResponsesGateway
participant Net as OkHttp on IO
UI->>VM: onEvent(Submit(text))
Note over VM: Main.immediate, isRunning=true, history += UserMessage pending-uuid
VM->>Repo: launch 1: persist(question snapshot)
VM->>AL: launch 2: run(historyBeforeTurn, text).collect
AL->>GW: stream(ModelRequest(policy.apply(history), specs))
GW->>GW: credentials.current(), settings.load().model
GW->>Net: POST /responses on Dispatchers.IO
loop SSE frames
Net-->>GW: frame
GW-->>AL: ModelEvent via channelFlow send
AL-->>VM: AgentEvent.TextDelta
VM-->>UI: StateFlow update, LazyColumn recomposes
end
opt reply has tool calls
AL->>AL: runToolCall for each call (flow b), next step, max 5
end
AL-->>VM: TurnCompleted(newItems)
VM->>VM: history = historyBeforeTurn + newItems
VM->>Repo: persist(history)
Repo-->>VM: observe() echo found in pendingOwnEmissions, UI not rebuilt
Note over VM: finally: unfinished tool cards (if any) to Cancelled, isRunning=false
```

Ошибка модели = `ModelEvent.Failed`, цикл отдаёт `TurnFailed(error, newItems)`; ViewModel сохраняет историю и показывает ошибку через `toPresentation()`. Исключение из цикла или инструмента ловит `collectTurn` (`ChatViewModel.kt:205-210`) и предлагает повтор.

Гонки и хрупкие места:
- `ChatViewModel.kt:32-58`: `history`, `historyBeforeTurn`, `toolMessageIdByCallId`, `pendingOwnEmissions` без синхронизации; верно, пока всё на Main.immediate.
- `:146-154`: два независимых `launch` (сохранить вопрос, выполнить ход). Порядок двух `replace()` держится на очереди Room; при мгновенной ошибке ход может записаться раньше вопроса. Гипотеза.
- `ResponsesGateway.kt:71,103`: `prompt_cache_key` новый на каждый `stream()`, то есть на каждый шаг, а не на диалог. Гипотеза о потере кэша промпта.
- `ResponsesGateway.kt:74-95`: `channelFlow` шлёт с IO-потока, коллектор читает на Main.

## (б) Вызов инструмента с подтверждением

```mermaid
sequenceDiagram
autonumber
participant AL as AgentLoop
participant VM as ChatViewModel
participant Pipe as ToolPipeline
participant Tool as AlarmTool
participant Br as UserInteractionBridge
participant Host as InteractionHost
AL->>Pipe: describe(name, args)
AL-->>VM: ToolCallStarted (card Running)
AL-->>VM: ApprovalRequested if risk is not SAFE (card AwaitingApproval)
AL->>Pipe: run(name, args)
Pipe->>Pipe: find in registry, check required fields
Pipe->>Br: confirm(ApprovalRequest(tool.describe, risk, args))
Br->>Br: pending = Approval(request, deferred), await
Host->>Br: collects pending, shows ApprovalSheet
alt user approves
Host->>Br: deferred.complete(true)
Br-->>Pipe: true
Pipe->>Tool: withTimeout 5 s: execute(args, ToolContext)
Tool-->>Pipe: ToolResult.Success
Pipe-->>AL: ToolOutcome.Success (truncated to 4 KB)
else user rejects or dismisses the sheet
Host->>Br: deferred.complete(false)
Br-->>Pipe: false
Pipe-->>AL: ToolOutcome.Rejected(user declined)
end
AL-->>VM: ToolCallFinished (card status)
Note over AL: ToolCallItem and ToolOutput go to newItems, model gets the output
```

Для `SAFE`-инструментов ветки подтверждения нет. `NotifyTool` и `SearchFilesTool` внутри `execute` зовут `bridge.ensure(capability)`: `InteractionHost` запускает системный запрос разрешения, ответ приходит через `deferred`. Текст подтверждения строится из `Tool.describe`, не из ответа модели. Неизвестный инструмент и ошибка аргументов дают `ToolOutcome.Failed`; модель получает `Failed: ...`.

Гонки и хрупкие места:
- `ToolPipeline.kt:85`, `NotifyTool.kt:59`, `SearchFilesTool.kt:78`: диалог разрешения внутри `withTimeout(5 с)` (наблюдение 1 в [обзоре](map-overview.md)).
- `UserInteractionBridge.kt:50,60`: один слот; второй параллельный вопрос затрёт первый. Сейчас вызовы идут по одному.
- `AgentLoop.kt:96-98`: `ApprovalRequested` уходит до `run()`; при `Failed` по аргументам окна не будет, карточка останется в `AwaitingApproval` до `ToolCallFinished`. Гипотеза, косметика.
- `ChatViewModel.kt:56-58`: `ApprovalRequested` без id привязан к последнему стартовавшему вызову; верно при последовательных вызовах.
- `InteractionHost.kt:27-29`: пересоздание Activity отклоняет открытое подтверждение.

## (в) Вход и обновление токена

```mermaid
sequenceDiagram
autonumber
participant UI as LoginScreen
participant Co as SessionAuthCoordinator
participant SM as SessionManager
participant BA as ChatGptBrowserAuth
participant Rx as LoopbackCallbackReceiver
participant TG as OkHttpTokenRefreshGateway
participant TS as EncryptedTokenStore
UI->>Co: signInWithBrowser() via LoginViewModel, viewModelScope
Co->>SM: signIn(CHATGPT_BROWSER)
SM->>SM: state = SigningIn
SM->>BA: signIn()
BA->>Rx: start(), bind 127.0.0.1:1455
BA->>BA: Pkce.create(), open Custom Tab
Rx-->>BA: awaitCallback on IO, timeout 3 min: code and state
BA->>BA: check state, wait for app foreground
BA->>TG: exchangeAuthorizationCode(code, verifier)
TG-->>BA: AuthTokens, retries 500 and 1500 ms on IOException
BA-->>SM: AuthResult.Success
SM->>TS: write(tokens) AES-GCM
SM->>SM: state = SignedIn(method, accountId from JWT)
SM-->>Co: state flow, then AppAuthStatus.SignedIn
Co-->>UI: LoginRoute shows AppNavigation
```

Другие способы: device code (запрос кода, `prompt` для UI, опрос до 15 мин, тот же обмен кода) и `API_KEY` (проверка `GET /models`). При старте `LoginViewModel.init` вызывает `restore()`.

```mermaid
sequenceDiagram
autonumber
participant GW as ResponsesGateway
participant SM as SessionManager
participant TR as TokenRefresher
participant TS as TokenStore
participant TG as TokenRefreshGateway
GW->>SM: current()
SM->>TR: current(forceRefresh=false)
TR->>TR: mutex.withLock, single-flight
TR->>TS: read()
alt no expiry, or valid longer than 30 s
TR-->>SM: Ready(tokens)
else expired or inside the 30 s window
TR->>TG: refresh(refreshToken)
alt success
TR->>TS: write(refreshed, method kept)
TR-->>SM: Ready
else invalid_grant
TR->>TS: clear()
TR-->>SM: NeedsSignIn
SM->>SM: reauthenticationFailure=InvalidGrant, state=SignedOut
else other failure
TR-->>SM: Failed(failure)
SM->>SM: state = Error
end
end
SM-->>GW: CredentialsResult Ready, SignedOut or Unavailable
GW->>GW: POST /responses
opt HTTP 401
GW->>SM: refreshAfterUnauthorized()
SM->>TR: current(forceRefresh=true)
GW->>GW: one retry with the new token, else SessionExpired
end
```

Гонки и хрупкие места:
- `TokenRefresher.kt:29-52`: `Mutex` защищает только refresh; `SessionManager.kt:63` (`write`) и `:82` (`clear`) вне него (наблюдение 7).
- `SessionManager.kt:50-79`: нет защиты от повторного `signIn` (UI блокирует кнопки по `isLoading`). `LoopbackCallbackReceiver` синглтон с полем `serverSocket` (`ChatGptBrowserAuth.kt:79`): второй параллельный `start()` закрыл бы сокет первого. Гипотеза.
- `ResponsesGateway.kt:97-116`: один повтор после 401; второй 401 = `SessionExpired`.
- `SessionAuthCoordinator.kt:98`: `pendingKey` без синхронизации между `setKey` и `signIn`.

## (г) Сохранение и восстановление истории

```mermaid
sequenceDiagram
autonumber
participant VM as ChatViewModel
participant Repo as RoomConversationRepository
participant Dao as ConversationDao
participant SVM as SettingsViewModel
Note over VM: init on Main.immediate
VM->>Repo: observe().collect
Repo->>Dao: observeAll() Flow
Dao-->>VM: first emission
Note over VM: history=loaded, UI = restored + typed, last item UserMessage means error Interrupted with Retry
VM->>VM: initialHistoryLoaded.complete()
VM->>Repo: persist(items): pendingOwnEmissions.addLast, replace(items)
Repo->>Dao: replaceAll(items), transaction: delete then insert
Dao-->>VM: emission with the same list
VM->>VM: indexOf in pendingOwnEmissions, drop entries up to it, skip UI rebuild
SVM->>Repo: replace(emptyList()) on Clear history
Dao-->>VM: emission [] not found in pendingOwnEmissions
VM->>VM: cancel running turn if any, messages cleared
```

Своя запись узнаётся по равенству всего списка (`ChatViewModel.kt:65-69`). Чужая (очистка из настроек) перестраивает ленту и отменяет идущий ход (`:70-75`). Ошибка записи: запись убирается из `pendingOwnEmissions`, `saveFailed=true`, показывается ошибка (`:176-194`). Ошибка `observe()` (битая строка): `Unexpected` в состоянии, лента пуста.

Гонки и хрупкие места:
- `ChatViewModel.kt:53,69,187`: `pendingOwnEmissions` чистится только совпадением или ошибкой записи; если Room объединит эмиссии или запись отменят после коммита, останутся лишние элементы `[проверить]`.
- `ConversationDao.kt:25-30`: каждое сохранение перезаписывает всю таблицу транзакцией `deleteAll` + `insertAll`.
- Сохранение только в `TurnCompleted`/`TurnFailed` (`ChatViewModel.kt:201`); убитый процесс оставляет вопрос без ответа, при старте это `Interrupted`.

## (д) Стоп

```mermaid
sequenceDiagram
autonumber
participant UI as Composer
participant VM as ChatViewModel
participant GW as ResponsesGateway
participant Http as OkHttp Call
participant Pipe as ToolPipeline
participant Br as UserInteractionBridge
UI->>VM: onEvent(Stop)
VM->>VM: runningJob.cancel(), isRunning=false at once
alt cancelled while waiting for the model
VM-->>GW: collector cancelled, channelFlow closes
GW->>Http: awaitClose: activeCall.cancel()
else cancelled inside a tool
VM-->>Pipe: withTimeout block cancelled
Pipe-->>Br: deferred.await cancelled, finally clears the slot
Note over Br: pending becomes null, InteractionHost hides the sheet
end
Note over VM: collectTurn finally: pending tool cards to Cancelled, map cleared, isRunning=false
Note over VM: no TurnCompleted, so no persist(history): the question was saved by launch 1, the partial answer and tool items are not
```

`CancellationException` везде пробрасывается (`collectTurn`, `ResponsesGateway`, `ToolPipeline` ловит только `TimeoutCancellationException`).

Гонки и хрупкие места:
- `ChatViewModel.kt:105-110` против `:211-216`: `isRunning=false` ставится сразу, а `finally` отменённой корутины сбрасывает его и `toolMessageIdByCallId` позже; новый `submit` между ними попадёт под сброс (наблюдение 9).
- `FindFilesTool.kt:87`, `SafTreeWalker.kt:52`: SAF-обход не проверяет отмену и идёт на Main, Stop его не прервёт.
- `ChatViewModel.kt:201`: экран остаётся с частичным ответом, на диске его нет; после перезапуска виден только вопрос.
