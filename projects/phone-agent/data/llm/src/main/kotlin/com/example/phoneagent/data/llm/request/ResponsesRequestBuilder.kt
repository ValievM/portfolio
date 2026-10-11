package com.example.phoneagent.data.llm.request

import com.example.phoneagent.data.llm.BackendProfile
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.model.ModelRequest
import com.example.phoneagent.domain.tools.ToolSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

// Effort is not user-configurable yet; "low" keeps replies snappy on a phone. Revisit once the
// model/settings picker exists (see docs/stages/3-model-client.md item 3.7).
private const val REASONING_EFFORT = "low"

/**
 * Builds the JSON body proven to work against the backend in spike/probe.py (see
 * docs/backend-notes.md "Формат запроса"). Assistant-message and tool-call-echo shapes below are
 * the standard Responses API convention but were not directly exercised by the stage-0 spike —
 * flagged in docs/backend-notes.md, to be confirmed by the stage-3 live multi-turn gate check.
 */
internal fun buildResponsesRequestBody(
    request: ModelRequest,
    profile: BackendProfile,
    model: String,
    sessionId: String,
): JsonObject =
    buildJsonObject {
        put("model", model)
        put("instructions", profile.instructions)
        putJsonArray("input") { request.history.forEach { add(it.toInputItem()) } }
        putJsonArray("tools") { request.tools.forEach { add(it.toToolJson()) } }
        put("tool_choice", "auto")
        put("parallel_tool_calls", true)
        putJsonObject("reasoning") { put("effort", REASONING_EFFORT) }
        put("store", false)
        put("stream", true)
        putJsonArray("include") { add("reasoning.encrypted_content") }
        put("prompt_cache_key", sessionId)
    }

private fun HistoryItem.toInputItem(): JsonObject =
    when (this) {
        is HistoryItem.UserMessage -> {
            buildJsonObject {
                put("type", "message")
                put("role", "user")
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "input_text")
                        put("text", text)
                    }
                }
            }
        }

        is HistoryItem.AssistantMessage -> {
            buildJsonObject {
                put("type", "message")
                put("role", "assistant")
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "output_text")
                        put("text", text)
                    }
                }
            }
        }

        is HistoryItem.ToolCallItem -> {
            buildJsonObject {
                put("type", "function_call")
                put("id", itemId.value)
                put("call_id", callId.value)
                put("name", name)
                put("arguments", Json.encodeToString(JsonObject.serializer(), arguments))
                put("status", "completed")
            }
        }

        is HistoryItem.ToolOutput -> {
            buildJsonObject {
                put("type", "function_call_output")
                put("call_id", callId.value)
                put("output", output)
            }
        }

        is HistoryItem.Opaque -> {
            payload.jsonObject
        }
    }

private fun ToolSpec.toToolJson(): JsonObject =
    buildJsonObject {
        put("type", "function")
        put("name", name)
        put("description", description)
        put("parameters", inputSchema)
        put("strict", false)
    }
