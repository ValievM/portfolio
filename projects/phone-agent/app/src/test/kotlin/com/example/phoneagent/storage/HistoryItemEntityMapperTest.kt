package com.example.phoneagent.storage

import com.example.phoneagent.domain.conversation.CallId
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.conversation.ItemId
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryItemEntityMapperTest {
    @Test
    fun `user message maps to room entity and back`() {
        val item = HistoryItem.UserMessage(itemId = ItemId("user-1"), text = "Hello")
        val entity = HistoryItemEntityMapper.toEntity(item, position = 0)

        assertEquals(item, HistoryItemEntityMapper.toDomain(entity))
    }

    @Test
    fun `assistant message maps to room entity and back`() {
        val item = HistoryItem.AssistantMessage(itemId = ItemId("assistant-1"), text = "Hi")
        val entity = HistoryItemEntityMapper.toEntity(item, position = 0)

        assertEquals(item, HistoryItemEntityMapper.toDomain(entity))
    }

    @Test
    fun `tool call maps identifiers name and nested arguments`() {
        val item =
            HistoryItem.ToolCallItem(
                itemId = ItemId("item-1"),
                callId = CallId("call-1"),
                name = "search_files",
                arguments =
                    buildJsonObject {
                        put("query", "notes")
                        put("filters", buildJsonObject { put("extension", "txt") })
                    },
            )
        val entity = HistoryItemEntityMapper.toEntity(item, position = 0)

        assertEquals("item-1", entity.id)
        assertEquals("call-1", entity.callId)
        assertEquals(item, HistoryItemEntityMapper.toDomain(entity))
    }

    @Test
    fun `tool output maps call identifier and output`() {
        val item = HistoryItem.ToolOutput(itemId = ItemId("output-1"), callId = CallId("call-1"), output = "Found 3 files")
        val entity = HistoryItemEntityMapper.toEntity(item, position = 0)

        assertEquals("output-1", entity.id)
        assertEquals("call-1", entity.callId)
        assertEquals(item, HistoryItemEntityMapper.toDomain(entity))
    }

    @Test
    fun `opaque item preserves provider and nested payload`() {
        val item =
            HistoryItem.Opaque(
                itemId = ItemId("opaque-1"),
                provider = "openai",
                payload =
                    buildJsonObject {
                        put("type", "reasoning")
                        put("details", buildJsonObject { put("encrypted", true) })
                    },
            )
        val entity = HistoryItemEntityMapper.toEntity(item, position = 0)

        assertEquals(item, HistoryItemEntityMapper.toDomain(entity))
    }

    @Test
    fun `legacy string Room rows restore tool identifiers and opaque payload unchanged`() {
        val legacyRows =
            listOf(
                ConversationEntity(
                    position = 0,
                    id = "fc_01aa497345b1cc25016ab1a1767a2487d2a9e6bb5666a9d3f3",
                    type = "tool_call",
                    callId = "call_lc67V6YnD8BFjY08ck8WFDSy",
                    name = "search_files",
                    argumentsJson = """{"query":"notes"}""",
                ),
                ConversationEntity(
                    position = 1,
                    id = "item-output-from-old-app",
                    type = "tool_output",
                    callId = "call_lc67V6YnD8BFjY08ck8WFDSy",
                    output = "Found 3 files",
                ),
                ConversationEntity(
                    position = 2,
                    id = "rs_legacy_reasoning_item",
                    type = "opaque",
                    provider = "openai",
                    payloadJson = """{"type":"reasoning","summary":["old"]}""",
                ),
            )

        val restored = legacyRows.map(HistoryItemEntityMapper::toDomain)

        assertEquals(
            listOf(
                HistoryItem.ToolCallItem(
                    itemId = ItemId("fc_01aa497345b1cc25016ab1a1767a2487d2a9e6bb5666a9d3f3"),
                    callId = CallId("call_lc67V6YnD8BFjY08ck8WFDSy"),
                    name = "search_files",
                    arguments = buildJsonObject { put("query", "notes") },
                ),
                HistoryItem.ToolOutput(
                    itemId = ItemId("item-output-from-old-app"),
                    callId = CallId("call_lc67V6YnD8BFjY08ck8WFDSy"),
                    output = "Found 3 files",
                ),
                HistoryItem.Opaque(
                    itemId = ItemId("rs_legacy_reasoning_item"),
                    provider = "openai",
                    payload =
                        buildJsonObject {
                            put("type", "reasoning")
                            put(
                                "summary",
                                kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("old")) },
                            )
                        },
                ),
            ),
            restored,
        )
    }
}
