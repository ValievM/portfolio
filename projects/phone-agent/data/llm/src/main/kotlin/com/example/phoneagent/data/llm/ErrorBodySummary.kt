package com.example.phoneagent.data.llm

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val MAX_IDENTIFIER_LENGTH = 64
private const val MAX_BODY_CHARS = 8_192
private val IDENTIFIER = Regex("[A-Za-z0-9_.\\[\\]-]+")
private val IDENTIFIER_FIELDS = listOf("type", "code", "param")

/**
 * A log-safe description of an error response. An error body can echo back parts of the request
 * (a chat message, a file name), and the log file outlives the chat, so the free-text message is
 * never included: only identifier-like values of `type`, `code` and `param`, or what kind of body
 * it was. Top-level key names are server-controlled and can themselves be a leaked file name, so
 * they are never echoed — only the key count is. Anything that does not look like an identifier
 * is dropped rather than shortened, and oversized bodies are summarized by size without parsing.
 */
internal fun summarizeErrorBody(body: String): String =
    when {
        body.isBlank() -> "empty body"
        body.length > MAX_BODY_CHARS -> "body of ${body.length} chars"
        else -> parseObject(body)?.let(::describe) ?: "non-JSON body of ${body.length} chars"
    }

private fun parseObject(body: String): JsonObject? =
    try {
        Json.parseToJsonElement(body) as? JsonObject
    } catch (_: SerializationException) {
        null
    }

private fun describe(json: JsonObject): String {
    val error = json["error"]
    val identifiers =
        when (error) {
            is JsonObject -> IDENTIFIER_FIELDS.mapNotNull { name -> identifier(error[name])?.let { "$name=$it" } }
            is JsonPrimitive -> listOfNotNull(identifier(error)?.let { "error=$it" })
            else -> emptyList()
        }
    return when {
        identifiers.isNotEmpty() -> identifiers.joinToString(" ")
        error != null -> "no error identifiers"
        else -> "json object, top-level keys: ${json.size}"
    }
}

private fun identifier(element: JsonElement?): String? =
    (element as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.contentOrNull
        ?.takeIf { it.length <= MAX_IDENTIFIER_LENGTH && IDENTIFIER.matches(it) }
