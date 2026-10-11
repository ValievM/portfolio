package com.example.phoneagent.data.llm.request

import com.example.phoneagent.data.llm.BackendProfile
import com.example.phoneagent.domain.conversation.CallId
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.conversation.ItemId
import com.example.phoneagent.domain.model.ModelRequest
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.ToolSpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsesRequestBuilderTest {
    @Test
    fun `store is false and stream is true`() {
        val body = buildResponsesRequestBody(request(), profile(), "gpt-test", "session-1")

        assertEquals(false, body.getValue("store").jsonPrimitive.boolean)
        assertEquals(true, body.getValue("stream").jsonPrimitive.boolean)
    }

    @Test
    fun `the model may return several tool calls in one response`() {
        val body = buildResponsesRequestBody(request(), profile(), "gpt-test", "session-1")

        assertEquals(true, body.getValue("parallel_tool_calls").jsonPrimitive.boolean)
    }

    @Test
    fun `instructions is not empty`() {
        val body = buildResponsesRequestBody(request(), profile(instructions = "be nice"), "gpt-test", "session-1")

        assertEquals("be nice", body.getValue("instructions").jsonPrimitive.content)
    }

    @Test
    fun `the model and session id are carried from their explicit arguments`() {
        val body =
            buildResponsesRequestBody(
                request = request(),
                profile = profile(),
                model = "gpt-selected",
                sessionId = "session-42",
            )

        assertEquals("gpt-selected", body.getValue("model").jsonPrimitive.content)
        assertEquals("session-42", body.getValue("prompt_cache_key").jsonPrimitive.content)
    }

    @Test
    fun `a user message becomes an input_text message item`() {
        val body =
            buildResponsesRequestBody(
                request(history = listOf(HistoryItem.UserMessage(ItemId("1"), "hi"))),
                profile(),
                "gpt-test",
                "session-1",
            )

        val item =
            body
                .getValue("input")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("message", item.getValue("type").jsonPrimitive.content)
        assertEquals("user", item.getValue("role").jsonPrimitive.content)
        val content =
            item
                .getValue("content")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("input_text", content.getValue("type").jsonPrimitive.content)
        assertEquals("hi", content.getValue("text").jsonPrimitive.content)
    }

    @Test
    fun `an assistant message becomes an output_text message item`() {
        val body =
            buildResponsesRequestBody(
                request(history = listOf(HistoryItem.AssistantMessage(ItemId("1"), "hello there"))),
                profile(),
                "gpt-test",
                "session-1",
            )

        val item =
            body
                .getValue("input")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("message", item.getValue("type").jsonPrimitive.content)
        assertEquals("assistant", item.getValue("role").jsonPrimitive.content)
        val content =
            item
                .getValue("content")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("output_text", content.getValue("type").jsonPrimitive.content)
        assertEquals("hello there", content.getValue("text").jsonPrimitive.content)
    }

    @Test
    fun `a tool call item is echoed with its id, call_id, name and arguments`() {
        val arguments = Json.parseToJsonElement("""{"city":"Москва"}""").jsonObject
        val body =
            buildResponsesRequestBody(
                request(history = listOf(HistoryItem.ToolCallItem(ItemId("item-1"), CallId("call-1"), "get_local_time", arguments))),
                profile(),
                "gpt-test",
                "session-1",
            )

        val item =
            body
                .getValue("input")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("function_call", item.getValue("type").jsonPrimitive.content)
        assertEquals("item-1", item.getValue("id").jsonPrimitive.content)
        assertEquals("call-1", item.getValue("call_id").jsonPrimitive.content)
        assertEquals("get_local_time", item.getValue("name").jsonPrimitive.content)
        assertEquals(arguments, Json.parseToJsonElement(item.getValue("arguments").jsonPrimitive.content))
    }

    @Test
    fun `a tool output becomes function_call_output using the same call_id`() {
        val body =
            buildResponsesRequestBody(
                request(history = listOf(HistoryItem.ToolOutput(ItemId("item-1"), CallId("call-1"), "14:32"))),
                profile(),
                "gpt-test",
                "session-1",
            )

        val item =
            body
                .getValue("input")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("function_call_output", item.getValue("type").jsonPrimitive.content)
        assertEquals("call-1", item.getValue("call_id").jsonPrimitive.content)
        assertEquals("14:32", item.getValue("output").jsonPrimitive.content)
    }

    @Test
    fun `tool call and output ids keep the exact previous wire JSON`() {
        val arguments = Json.parseToJsonElement("""{"city":"Москва"}""").jsonObject
        val history =
            listOf(
                HistoryItem.ToolCallItem(
                    ItemId("fc_01aa497345b1cc25016ab1a1767a2487d2a9e6bb5666a9d3f3"),
                    CallId("call_lc67V6YnD8BFjY08ck8WFDSy"),
                    "get_local_time",
                    arguments,
                ),
                HistoryItem.ToolOutput(
                    ItemId("output-id-is-not-sent"),
                    CallId("call_lc67V6YnD8BFjY08ck8WFDSy"),
                    "14:32",
                ),
            )
        val body = buildResponsesRequestBody(request(history), profile(), "gpt-test", "session-1")
        val input = body.getValue("input").jsonArray

        val expectedFunctionCall =
            """{"type":"function_call","id":"fc_01aa497345b1cc25016ab1a1767a2487d2a9e6bb5666a9d3f3",""" +
                """"call_id":"call_lc67V6YnD8BFjY08ck8WFDSy","name":"get_local_time",""" +
                """"arguments":"{\"city\":\"Москва\"}","status":"completed"}"""
        assertEquals(expectedFunctionCall, Json.encodeToString(input[0].jsonObject))
        assertEquals(
            """{"type":"function_call_output","call_id":"call_lc67V6YnD8BFjY08ck8WFDSy","output":"14:32"}""",
            Json.encodeToString(input[1].jsonObject),
        )
    }

    @Test
    fun `tools are translated to the function tool schema the backend accepts`() {
        val schema = Json.parseToJsonElement("""{"type":"object","properties":{}}""").jsonObject
        val tool = ToolSpec(name = "get_local_time", description = "current time", inputSchema = schema, risk = Risk.SAFE)
        val body = buildResponsesRequestBody(request(tools = listOf(tool)), profile(), "gpt-test", "session-1")

        val toolJson =
            body
                .getValue("tools")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("function", toolJson.getValue("type").jsonPrimitive.content)
        assertEquals("get_local_time", toolJson.getValue("name").jsonPrimitive.content)
        assertEquals("current time", toolJson.getValue("description").jsonPrimitive.content)
        assertEquals(schema, toolJson.getValue("parameters").jsonObject)
        assertTrue(body.getValue("tool_choice").jsonPrimitive.content == "auto")
    }

    private fun request(
        history: List<HistoryItem> = emptyList(),
        tools: List<ToolSpec> = emptyList(),
    ): ModelRequest = ModelRequest(history, tools)

    private fun profile(instructions: String = "instructions"): BackendProfile =
        BackendProfile(baseUrl = "https://example.test", instructions = instructions)
}
