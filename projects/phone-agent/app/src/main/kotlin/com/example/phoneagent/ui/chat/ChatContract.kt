package com.example.phoneagent.ui.chat

import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.conversation.TurnState
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.ui.designsystem.components.FileHit
import com.example.phoneagent.ui.designsystem.components.ToolCallStatus
import kotlinx.serialization.json.JsonObject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val turn: TurnState = TurnState.Idle,
    val historySync: HistorySyncState = HistorySyncState.Loading,
)

sealed interface HistorySyncState {
    data object Loading : HistorySyncState

    data object Ready : HistorySyncState

    data class Failed(
        val operation: StorageOperation,
        val error: AgentError =
            when (operation) {
                StorageOperation.LOAD -> AgentError.Unexpected("Failed to load conversation history")
                StorageOperation.SAVE -> AgentError.Unexpected("Failed to save conversation history")
            },
    ) : HistorySyncState
}

enum class StorageOperation {
    LOAD,
    SAVE,
}

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    /** Non-null only for [ChatRole.TOOL]; drives [com.example.phoneagent.ui.designsystem.components.ToolCallCard]. */
    val toolCall: ToolCallMessage? = null,
)

data class ToolCallMessage(
    val status: ToolCallStatus,
    val description: CallDescription,
    val arguments: JsonObject,
    val expanded: Boolean = false,
    val fileHits: List<FileHit> = emptyList(),
)

enum class ChatRole {
    USER,
    ASSISTANT,
    TOOL,
}

sealed interface ChatUiEvent {
    data class Submit(
        val text: String,
    ) : ChatUiEvent

    data object Stop : ChatUiEvent

    data class ToggleToolCallDetails(
        val messageId: String,
    ) : ChatUiEvent

    data object Retry : ChatUiEvent

    data object DismissError : ChatUiEvent
}
