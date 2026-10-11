package com.example.phoneagent.ui.designsystem.components

/**
 * Self-sufficient UI state of a tool call in the chat feed. Deliberately independent of
 * AgentEvent and ToolOutcome so the integration layer can map any event source onto it.
 * [Done] carries the short result line, [Failed] the failure reason; the other statuses have
 * fixed labels from strings.xml.
 */
sealed interface ToolCallStatus {
    data object AwaitingApproval : ToolCallStatus

    data object Running : ToolCallStatus

    data class Done(
        val summary: String,
    ) : ToolCallStatus

    data class FilesFound(
        val count: Int,
    ) : ToolCallStatus

    data class Failed(
        val reason: String,
    ) : ToolCallStatus

    data object Rejected : ToolCallStatus

    data object Cancelled : ToolCallStatus
}
