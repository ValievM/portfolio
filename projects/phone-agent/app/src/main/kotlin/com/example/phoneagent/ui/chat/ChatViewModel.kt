package com.example.phoneagent.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.agent.AgentEvent
import com.example.phoneagent.domain.agent.AgentLoop
import com.example.phoneagent.domain.conversation.ApprovedCall
import com.example.phoneagent.domain.conversation.CallId
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.conversation.ItemId
import com.example.phoneagent.domain.conversation.PendingApproval
import com.example.phoneagent.domain.conversation.ToolCall
import com.example.phoneagent.domain.conversation.TurnEvent
import com.example.phoneagent.domain.conversation.TurnState
import com.example.phoneagent.domain.conversation.transitionTurn
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.platform.IdGenerator
import com.example.phoneagent.domain.platform.RandomIdGenerator
import com.example.phoneagent.domain.storage.ConversationRepository
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.ToolOutcome
import com.example.phoneagent.ui.designsystem.components.FileHit
import com.example.phoneagent.ui.designsystem.components.ToolCallStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel
    @Inject
    constructor(
        private val agentLoop: AgentLoop,
        private val conversationRepository: ConversationRepository,
        // Default only keeps the dozens of existing test constructions unchanged; DI always passes
        // the bound generator (see AgentModule).
        private val idGenerator: IdGenerator = RandomIdGenerator,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(ChatUiState())
        private var nextMessageId = 0L
        private var runningJob: Job? = null

        // Identity of the newest submitted turn: a cancelled turn's finally must not touch a newer one.
        private var currentTurn: Any? = null

        // Persisted snapshots are authoritative for history; ChatMessage remains a UI-only rendering
        // concern and is never converted back into HistoryItem.
        private var history: List<HistoryItem> = emptyList()

        // What `history` was before the running turn appended its provisional question; the
        // finished turn rebuilds history from this, so the question is saved exactly once.
        private var historyBeforeTurn: List<HistoryItem> = emptyList()
        private val initialHistoryLoaded = CompletableDeferred<Unit>()

        // A successful later save clears only a stale save failure, never a load or turn failure.
        private var saveFailed = false

        // Every history we wrote and have not yet seen come back through observe(); a turn now saves
        // twice (the question, then the finished turn), so one slot could mistake the first echo for
        // an outside change and rebuild the feed from a stale list.
        private val pendingOwnEmissions = ArrayDeque<List<HistoryItem>>()
        private val toolMessageIdByCallId = mutableMapOf<CallId, String>()
        private val approvalRequestByCallId = mutableMapOf<CallId, ApprovalRequest>()
        private val agentEventHandler = AgentEventHandler()

        // Single approval events do not carry a call id; AgentLoop runs tool calls sequentially.
        private var lastToolCallId: CallId? = null
        private var lastToolMessageId: String? = null

        val state: StateFlow<ChatUiState> = mutableState.asStateFlow()

        init {
            viewModelScope.launch {
                try {
                    conversationRepository.observe().collect { loadedHistory ->
                        val isInitialEmission = !initialHistoryLoaded.isCompleted
                        val ownEmissionIndex = pendingOwnEmissions.indexOf(loadedHistory)
                        val isOwnHistoryEmission = ownEmissionIndex >= 0
                        repeat(ownEmissionIndex + 1) { pendingOwnEmissions.removeFirstOrNull() }
                        val interruptedActiveTurn =
                            !isInitialEmission && !isOwnHistoryEmission && runningJob?.isActive == true
                        if (interruptedActiveTurn) {
                            runningJob?.cancel()
                            runningJob = null
                        }
                        history = loadedHistory
                        // The screen already shows our own turn exactly as it happened, so only history
                        // that came from elsewhere (first load, Settings, another writer) is redrawn.
                        if (!isOwnHistoryEmission) {
                            mutableState.update {
                                it.withLoadedHistory(
                                    loadedHistory,
                                    isInitialEmission,
                                    describe = { call ->
                                        agentLoop.describe(
                                            ToolCall(
                                                callId = call.callId,
                                                name = call.name,
                                                arguments = call.arguments,
                                                itemId = call.itemId,
                                            ),
                                        )
                                    },
                                    restoreContent = agentLoop::restoreToolContent,
                                )
                            }
                        }
                        initialHistoryLoaded.complete(Unit)
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    mutableState.update {
                        it.copy(
                            historySync =
                                HistorySyncState.Failed(
                                    StorageOperation.LOAD,
                                    AgentError.Unexpected("Failed to load conversation history (${exception::class.simpleName})"),
                                ),
                        )
                    }
                } finally {
                    initialHistoryLoaded.complete(Unit)
                }
            }
        }

        fun onEvent(event: ChatUiEvent) {
            when (event) {
                is ChatUiEvent.Submit -> {
                    submit(event.text)
                }

                ChatUiEvent.Stop -> {
                    mutableState.update { state -> state.copy(turn = transitionTurn(state.turn, TurnEvent.Stop)) }
                    cancelPendingToolCards()
                    runningJob?.cancel()
                }

                is ChatUiEvent.ToggleToolCallDetails -> {
                    mutableState.update { it.withToolCallToggled(event.messageId) }
                }

                ChatUiEvent.Retry -> {
                    retry()
                }

                ChatUiEvent.DismissError -> {
                    saveFailed = false
                    mutableState.update { state ->
                        val clearTurnFailure = state.turn.hasRetryAction() || state.turn is TurnState.FailedTerminal
                        state.copy(
                            turn = if (clearTurnFailure) transitionTurn(state.turn, TurnEvent.DismissFailure) else state.turn,
                            historySync = if (state.historySync is HistorySyncState.Failed) HistorySyncState.Ready else state.historySync,
                        )
                    }
                }
            }
        }

        private fun submit(
            text: String,
            retry: Boolean = false,
        ) {
            if (text.isBlank()) return
            val trimmed = text.trim()
            val questionId = ItemId("pending-${idGenerator.newItemId().value}")
            val stateBeforeSubmit = mutableState.value
            val startEvent = if (retry) TurnEvent.Retry(questionId) else TurnEvent.Submitted(questionId)
            val nextTurn = transitionTurn(stateBeforeSubmit.turn, startEvent)
            if (nextTurn == stateBeforeSubmit.turn) return

            mutableState.update {
                it.copy(
                    messages = it.messages + ChatMessage(nextId(), ChatRole.USER, trimmed),
                    turn = nextTurn,
                    historySync = if (it.historySync is HistorySyncState.Failed) HistorySyncState.Ready else it.historySync,
                )
            }

            // The question enters the in-memory history immediately - not via the storage echo -
            // so a Stop before the echo cannot lose it, and the next submit cannot overwrite it.
            historyBeforeTurn = history
            // The id must be unique: a stopped question stays in history, and the feed keys on it.
            history = history + HistoryItem.UserMessage(questionId, trimmed)
            // Saved in its own job: stopping the turn must not cancel the save of the question.
            val questionSnapshot = history
            viewModelScope.launch {
                initialHistoryLoaded.await()
                persist(questionSnapshot)
            }
            // The token is set before the launch: the turn's finally uses it to tell "I am still
            // the newest turn" from "a newer submit happened while I was being cancelled".
            val turnToken = Any()
            currentTurn = turnToken
            runningJob =
                viewModelScope.launch {
                    initialHistoryLoaded.await()
                    collectTurn(trimmed, turnToken)
                }
        }

        /**
         * Redoes the turn that just failed (or was cut short by a closed app): the failed exchange
         * (the user message and whatever the model managed to produce) is dropped from memory and
         * screen first, and [submit] saves the question alone, so the text is sent once and a Stop
         * during the retry cannot bring the failed attempt back.
         */
        private fun retry() {
            val current = mutableState.value
            if (!current.turn.hasRetryAction()) return
            val failedUserIndex = history.indexOfLast { it is HistoryItem.UserMessage }
            val failedUser = history.getOrNull(failedUserIndex) as? HistoryItem.UserMessage ?: return
            val keptHistory = history.take(failedUserIndex)
            val keptMessages = current.messages.take(current.messages.indexOfLast { it.role == ChatRole.USER }.coerceAtLeast(0))

            history = keptHistory
            mutableState.update { it.copy(messages = keptMessages) }
            submit(failedUser.text, retry = true)
        }

        private suspend fun persist(items: List<HistoryItem>) {
            pendingOwnEmissions.addLast(items)
            try {
                conversationRepository.replace(items)
                if (saveFailed) {
                    saveFailed = false
                    mutableState.update { state ->
                        if (state.historySync == HistorySyncState.Failed(StorageOperation.SAVE)) {
                            state.copy(historySync = HistorySyncState.Ready)
                        } else {
                            state
                        }
                    }
                }
            } catch (exception: CancellationException) {
                pendingOwnEmissions.remove(items)
                throw exception
            } catch (_: Exception) {
                pendingOwnEmissions.remove(items)
                saveFailed = true
                mutableState.update { it.copy(historySync = HistorySyncState.Failed(StorageOperation.SAVE)) }
            }
        }

        private suspend fun collectTurn(
            text: String,
            turnToken: Any,
        ) {
            try {
                var assistantMessageId: String? = null
                agentLoop
                    .run(historyBeforeTurn, text) { callIds ->
                        updateTurn(TurnEvent.ApprovalResolved(callIds))
                    }.collect { event ->
                        assistantMessageId = agentEventHandler.handle(event, assistantMessageId)
                        when (event) {
                            is AgentEvent.TurnCompleted -> {
                                persist(history)
                                updateTurn(TurnEvent.Completed)
                            }

                            is AgentEvent.TurnFailed -> {
                                clearPriorSaveFailure()
                                updateTurn(TurnEvent.Failed(event.error, event.error.toPresentation()?.retryable == true))
                                persist(history)
                            }

                            else -> {
                                Unit
                            }
                        }
                    }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                // A bug in the loop, a gateway or a tool must not take the app down: keep the
                // question in history and offer a retry of the whole turn.
                if (currentTurn === turnToken) {
                    clearPriorSaveFailure()
                    updateTurn(TurnEvent.Failed(AgentError.Unexpected("turn failed (${exception::class.simpleName})"), retryable = true))
                }
            } finally {
                if (currentTurn === turnToken) {
                    cancelPendingToolCards()
                    mutableState.update { state ->
                        if (state.turn.isInProgress()) state.copy(turn = transitionTurn(state.turn, TurnEvent.Stop)) else state
                    }
                }
            }
        }

        private inner class AgentEventHandler {
            fun handle(
                event: AgentEvent,
                assistantMessageId: String?,
            ): String? =
                when (event) {
                    is AgentEvent.TextDelta -> {
                        appendAssistantDelta(event, assistantMessageId)
                    }

                    is AgentEvent.ToolCallStarted -> {
                        addToolCallCard(event)
                    }

                    is AgentEvent.BatchApprovalRequested -> {
                        requestBatchApproval(event)
                        assistantMessageId
                    }

                    is AgentEvent.ApprovalRequested -> {
                        requestSingleApproval(event)
                        assistantMessageId
                    }

                    is AgentEvent.ToolCallFinished -> {
                        finishToolCall(event)
                        assistantMessageId
                    }

                    is AgentEvent.TurnCompleted -> {
                        updateHistory(event.newItems, assistantMessageId)
                    }

                    is AgentEvent.TurnFailed -> {
                        updateHistory(event.newItems, assistantMessageId)
                    }
                }

            private fun appendAssistantDelta(
                event: AgentEvent.TextDelta,
                assistantMessageId: String?,
            ): String {
                val id = assistantMessageId ?: nextId()
                mutableState.update { it.withAssistantDelta(id, event.text) }
                return id
            }

            private fun addToolCallCard(event: AgentEvent.ToolCallStarted): String? {
                val id = nextId()
                toolMessageIdByCallId[event.call.callId] = id
                approvalRequestByCallId[event.call.callId] = ApprovalRequest(event.description, Risk.CONFIRM, event.call.arguments)
                lastToolCallId = event.call.callId
                lastToolMessageId = id
                val card = ToolCallMessage(ToolCallStatus.Running, event.description, event.call.arguments)
                mutableState.update { it.copy(messages = it.messages + ChatMessage(id, ChatRole.TOOL, "", toolCall = card)) }
                return null
            }

            private fun requestBatchApproval(event: AgentEvent.BatchApprovalRequested) {
                val cardIds = event.callIds.mapNotNull { toolMessageIdByCallId[it] }
                val approvedCalls = event.callIds.mapNotNull { callId -> approvalRequestByCallId[callId]?.let { ApprovedCall(callId, it) } }
                mutableState.update { state ->
                    val withCards = cardIds.fold(state) { acc, id -> acc.withToolCallStatus(id, ToolCallStatus.AwaitingApproval) }
                    if (approvedCalls.size == event.callIds.size) {
                        withCards.copy(
                            turn = transitionTurn(withCards.turn, TurnEvent.ApprovalRequired(PendingApproval.Batch(approvedCalls))),
                        )
                    } else {
                        withCards
                    }
                }
            }

            private fun requestSingleApproval(event: AgentEvent.ApprovalRequested) {
                lastToolMessageId?.let { id -> mutableState.update { it.withToolCallStatus(id, ToolCallStatus.AwaitingApproval) } }
                lastToolCallId?.let { callId -> updateTurn(TurnEvent.ApprovalRequired(PendingApproval.Single(callId, event.request))) }
            }

            private fun updateHistory(
                newItems: List<HistoryItem>,
                assistantMessageId: String?,
            ): String? {
                // newItems carries the real user message, replacing the provisional one.
                history = historyBeforeTurn + newItems
                return assistantMessageId
            }
        }

        private fun updateTurn(event: TurnEvent) {
            mutableState.update { state -> state.copy(turn = transitionTurn(state.turn, event)) }
        }

        private fun clearPriorSaveFailure() {
            val sync = mutableState.value.historySync as? HistorySyncState.Failed ?: return
            if (sync.operation != StorageOperation.SAVE) return
            saveFailed = false
            mutableState.update { state ->
                if (state.historySync is HistorySyncState.Failed && state.historySync.operation == StorageOperation.SAVE) {
                    state.copy(historySync = HistorySyncState.Ready)
                } else {
                    state
                }
            }
        }

        private fun cancelPendingToolCards() {
            val pendingIds = toolMessageIdByCallId.values.toList()
            toolMessageIdByCallId.clear()
            approvalRequestByCallId.clear()
            lastToolCallId = null
            lastToolMessageId = null
            mutableState.update { state -> pendingIds.fold(state) { acc, id -> acc.withToolCallStatus(id, ToolCallStatus.Cancelled) } }
        }

        private fun finishToolCall(event: AgentEvent.ToolCallFinished) {
            val id = toolMessageIdByCallId.remove(event.call.callId) ?: return
            approvalRequestByCallId.remove(event.call.callId)
            val fileHits = event.outcome.toFileHits()
            val status = event.outcome.toCardStatus(fileHits.size)
            mutableState.update { it.withToolCallStatus(id, status, fileHits) }
        }

        private fun nextId(): String = (++nextMessageId).toString()
    }
