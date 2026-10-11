package com.example.phoneagent.ui.chat

import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.conversation.TurnEvent
import com.example.phoneagent.domain.conversation.TurnState
import com.example.phoneagent.domain.conversation.transitionTurn
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.ToolContent
import com.example.phoneagent.domain.tools.ToolOutcome
import com.example.phoneagent.domain.tools.toolOutcomeFromOutputText
import com.example.phoneagent.ui.designsystem.components.FileHit
import com.example.phoneagent.ui.designsystem.components.ToolCallStatus

/** Appends streamed text to the assistant message [id], creating the message on the first chunk. */
internal fun ChatUiState.withAssistantDelta(
    id: String,
    delta: String,
): ChatUiState {
    val existing = messages.firstOrNull { it.id == id }
    val updated =
        if (existing == null) {
            messages + ChatMessage(id, ChatRole.ASSISTANT, delta)
        } else {
            messages.map { message -> if (message.id == id) message.copy(text = message.text + delta) else message }
        }
    return copy(messages = updated)
}

/** Sets the status of the tool call card [id]; [fileHits] replaces the attached hits only when given. */
internal fun ChatUiState.withToolCallStatus(
    id: String,
    status: ToolCallStatus,
    fileHits: List<FileHit>? = null,
): ChatUiState = copy(messages = messages.mapToolCall(id) { it.copy(status = status, fileHits = fileHits ?: it.fileHits) })

internal fun ChatUiState.withToolCallToggled(id: String): ChatUiState =
    copy(messages = messages.mapToolCall(id) { it.copy(expanded = !it.expanded) })

private fun List<ChatMessage>.mapToolCall(
    id: String,
    transform: (ToolCallMessage) -> ToolCallMessage,
): List<ChatMessage> =
    map { message ->
        val toolCall = message.toolCall
        if (message.id == id && toolCall != null) message.copy(toolCall = transform(toolCall)) else message
    }

/**
 * The feed after the saved history changed underneath it. On the first emission the restored
 * messages go before anything already typed; a later empty history means it was cleared elsewhere
 * (Settings). A history that ends with a user message on start-up means the app was closed before
 * the answer arrived: that is shown as an interrupted turn that can be retried.
 *
 * Tool cards are part of the saved history, so they are drawn again from it ([describe] gives the card
 * its text) and a change from outside never leaves a live card next to its restored twin.
 */
internal fun ChatUiState.withLoadedHistory(
    loaded: List<HistoryItem>,
    isInitial: Boolean,
    describe: (HistoryItem.ToolCallItem) -> CallDescription,
    restoreContent: (String, String) -> ToolContent? = { _, _ -> null },
): ChatUiState {
    val restored =
        when {
            isInitial -> loaded.toChatMessages(describe, restoreContent) + messages
            loaded.isEmpty() -> emptyList()
            else -> loaded.toChatMessages(describe, restoreContent)
        }
    val unanswered = loaded.lastOrNull() as? HistoryItem.UserMessage
    val nextTurn =
        if (isInitial) {
            if (!turn.isInProgress() && unanswered != null) {
                transitionTurn(turn, TurnEvent.RestoredUnanswered(unanswered.itemId))
            } else {
                turn
            }
        } else {
            transitionTurn(turn, TurnEvent.ExternalHistoryChanged(unanswered?.itemId))
        }
    return copy(
        messages = restored,
        turn = nextTurn,
        historySync = if (isInitial || loaded.isEmpty()) HistorySyncState.Ready else historySync,
    )
}

internal fun TurnState.isInProgress(): Boolean = this is TurnState.Running || this is TurnState.AwaitingApproval

internal fun TurnState.hasRetryAction(): Boolean = this is TurnState.FailedRetryable || this is TurnState.Interrupted

internal fun ChatUiState.errorForDisplay(): AgentError? {
    val syncError = (historySync as? HistorySyncState.Failed)?.error
    return syncError ?: when (val currentTurn = turn) {
        is TurnState.FailedRetryable -> currentTurn.error
        is TurnState.FailedTerminal -> currentTurn.error
        is TurnState.Interrupted -> AgentError.Interrupted
        is TurnState.Idle, is TurnState.Running, is TurnState.AwaitingApproval, is TurnState.Stopped -> null
    }
}

/**
 * Draws the saved history: texts as messages, and every tool call as a card where the call happened, with
 * the status read from its saved result (found by call id). A call without a result was cut short.
 */
internal fun List<HistoryItem>.toChatMessages(
    describe: (HistoryItem.ToolCallItem) -> CallDescription,
    restoreContent: (String, String) -> ToolContent? = { _, _ -> null },
): List<ChatMessage> {
    val outputs = filterIsInstance<HistoryItem.ToolOutput>().associateBy(HistoryItem.ToolOutput::callId)
    return mapNotNull { item ->
        when (item) {
            is HistoryItem.UserMessage -> {
                ChatMessage(item.itemId.value, ChatRole.USER, item.text)
            }

            is HistoryItem.AssistantMessage -> {
                ChatMessage(item.itemId.value, ChatRole.ASSISTANT, item.text)
            }

            is HistoryItem.ToolCallItem -> {
                ChatMessage(item.itemId.value, ChatRole.TOOL, "", item.toCard(outputs[item.callId], describe, restoreContent))
            }

            is HistoryItem.ToolOutput, is HistoryItem.Opaque -> {
                null
            }
        }
    }
}

private fun HistoryItem.ToolCallItem.toCard(
    output: HistoryItem.ToolOutput?,
    describe: (HistoryItem.ToolCallItem) -> CallDescription,
    restoreContent: (String, String) -> ToolContent?,
): ToolCallMessage {
    val outcome =
        output?.let { toolOutcomeFromOutputText(it.output) }?.let { restoredOutcome ->
            if (restoredOutcome is ToolOutcome.Success) {
                restoredOutcome.copy(
                    content = restoreContent(name, restoredOutcome.text),
                )
            } else {
                restoredOutcome
            }
        }
    val fileHits = outcome?.toFileHits().orEmpty()
    return ToolCallMessage(
        status = outcome?.toCardStatus(fileHits.size) ?: ToolCallStatus.Cancelled,
        description = describe(this),
        arguments = arguments,
        fileHits = fileHits,
    )
}
