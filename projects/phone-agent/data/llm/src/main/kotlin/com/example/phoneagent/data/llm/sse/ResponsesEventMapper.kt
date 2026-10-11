package com.example.phoneagent.data.llm.sse

import com.example.phoneagent.data.llm.dto.ErrorEventDto
import com.example.phoneagent.data.llm.dto.OutputItemDoneDto
import com.example.phoneagent.data.llm.dto.OutputItemDto
import com.example.phoneagent.data.llm.dto.OutputTextDeltaDto
import com.example.phoneagent.data.llm.dto.ResponseCompletedDto
import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.conversation.CallId
import com.example.phoneagent.domain.conversation.ItemId
import com.example.phoneagent.domain.conversation.ToolCall
import com.example.phoneagent.domain.model.ModelEvent
import com.example.phoneagent.domain.model.Usage
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

private val relaxedJson = Json { ignoreUnknownKeys = true }

private const val EVENT_TEXT_DELTA = "response.output_text.delta"
private const val EVENT_OUTPUT_ITEM_DONE = "response.output_item.done"
private const val EVENT_COMPLETED = "response.completed"
private const val EVENT_ERROR = "error"
private const val ITEM_TYPE_FUNCTION_CALL = "function_call"

/**
 * Maps one raw SSE frame to a domain [ModelEvent], or `null` for frames we intentionally don't
 * act on (progress markers, partial function-call argument deltas, etc. — see
 * docs/backend-notes.md for the full list of events actually observed from the backend).
 */
internal fun RawSseEvent.toModelEvent(): ModelEvent? =
    try {
        when (name) {
            EVENT_TEXT_DELTA -> ModelEvent.TextDelta(relaxedJson.decodeFromString(OutputTextDeltaDto.serializer(), data).delta)
            EVENT_OUTPUT_ITEM_DONE -> functionCallOrNull()
            EVENT_COMPLETED -> completed()
            EVENT_ERROR -> failedFromError()
            else -> null
        }
    } catch (_: SerializationException) {
        ModelEvent.Failed(AgentError.ProtocolChanged)
    }

private fun RawSseEvent.functionCallOrNull(): ModelEvent? {
    val item = relaxedJson.decodeFromString(OutputItemDoneDto.serializer(), data).item
    if (item.type != ITEM_TYPE_FUNCTION_CALL) return null
    return item.toToolCallOrNull()?.let(ModelEvent::ToolCallRequested) ?: ModelEvent.Failed(AgentError.ProtocolChanged)
}

private fun OutputItemDto.toToolCallOrNull(): ToolCall? {
    if (listOf(id, callId, name, arguments).any { it == null }) return null
    val parsedArguments = relaxedJson.parseToJsonElement(requireNotNull(arguments)).jsonObject
    return ToolCall(
        callId = CallId(requireNotNull(callId)),
        name = requireNotNull(name),
        arguments = parsedArguments,
        itemId = ItemId(requireNotNull(id)),
    )
}

private fun RawSseEvent.completed(): ModelEvent {
    val usage = relaxedJson.decodeFromString(ResponseCompletedDto.serializer(), data).response.usage
    return ModelEvent.Completed(usage?.let { Usage(it.inputTokens, it.outputTokens) })
}

private fun RawSseEvent.failedFromError(): ModelEvent {
    val message = relaxedJson.decodeFromString(ErrorEventDto.serializer(), data).error?.message
    return ModelEvent.Failed(AgentError.Unexpected(message ?: "unknown SSE error"))
}
