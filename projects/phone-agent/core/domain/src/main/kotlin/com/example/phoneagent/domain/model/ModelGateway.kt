package com.example.phoneagent.domain.model

import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.conversation.ToolCall
import com.example.phoneagent.domain.tools.ToolSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement

public interface ModelGateway {
    public fun stream(request: ModelRequest): Flow<ModelEvent>
}

public data class ModelRequest(
    public val history: List<HistoryItem>,
    public val tools: List<ToolSpec> = emptyList(),
)

public sealed interface ModelEvent {
    public data class TextDelta(
        public val text: String,
    ) : ModelEvent

    public data class ToolCallRequested(
        public val call: ToolCall,
    ) : ModelEvent

    public data class OpaqueItem(
        public val provider: String,
        public val payload: JsonElement,
    ) : ModelEvent

    public data class Completed(
        public val usage: Usage? = null,
    ) : ModelEvent

    public data class Failed(
        public val error: AgentError,
    ) : ModelEvent
}

public data class Usage(
    public val inputTokens: Int,
    public val outputTokens: Int,
)
