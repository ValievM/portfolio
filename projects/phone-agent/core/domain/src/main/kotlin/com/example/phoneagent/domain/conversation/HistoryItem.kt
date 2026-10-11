package com.example.phoneagent.domain.conversation

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@JvmInline
public value class CallId(
    public val value: String,
)

@JvmInline
public value class ItemId(
    public val value: String,
)

public sealed interface HistoryItem {
    public val itemId: ItemId

    public data class UserMessage(
        override val itemId: ItemId,
        public val text: String,
    ) : HistoryItem

    public data class AssistantMessage(
        override val itemId: ItemId,
        public val text: String,
    ) : HistoryItem

    public data class ToolCallItem(
        override val itemId: ItemId,
        public val callId: CallId,
        public val name: String,
        public val arguments: JsonObject,
    ) : HistoryItem

    public data class ToolOutput(
        override val itemId: ItemId,
        public val callId: CallId,
        public val output: String,
    ) : HistoryItem

    public data class Opaque(
        override val itemId: ItemId,
        public val provider: String,
        public val payload: JsonElement,
    ) : HistoryItem
}

public data class ToolCall(
    public val callId: CallId,
    public val name: String,
    public val arguments: JsonObject,
    /** Provider-issued ID of the function-call item, required when echoing it in the next turn. */
    public val itemId: ItemId = ItemId(callId.value),
)
