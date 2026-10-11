package com.example.phoneagent.data.llm.sse

import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.conversation.CallId
import com.example.phoneagent.domain.conversation.ItemId
import com.example.phoneagent.domain.conversation.ToolCall
import com.example.phoneagent.domain.model.ModelEvent
import com.example.phoneagent.domain.model.Usage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ResponsesEventMapperTest {
    @Test
    fun `a tool call turn maps to ToolCallRequested then Completed, ignoring intermediate events`() {
        val events = mappedEvents(turn(1))

        assertEquals(
            listOf(
                ModelEvent.ToolCallRequested(
                    ToolCall(
                        callId = CallId("call_lc67V6YnD8BFjY08ck8WFDSy"),
                        name = "get_local_time",
                        arguments = Json.parseToJsonElement("""{"city":"Москва"}""").jsonObject,
                        itemId = ItemId("fc_01aa497345b1cc25016ab1a1767a2487d2a9e6bb5666a9d3f3"),
                    ),
                ),
                ModelEvent.Completed(Usage(inputTokens = 160, outputTokens = 19)),
            ),
            events,
        )
    }

    @Test
    fun `empty provider identifiers are retained without validation`() {
        val event =
            RawSseEvent(
                "response.output_item.done",
                """{"item":{"id":"","call_id":"","type":"function_call","name":"get_local_time","arguments":"{}"}}""",
            ).toModelEvent()

        assertEquals(
            ModelEvent.ToolCallRequested(
                ToolCall(
                    callId = CallId(""),
                    name = "get_local_time",
                    arguments = Json.parseToJsonElement("{}").jsonObject,
                    itemId = ItemId(""),
                ),
            ),
            event,
        )
    }

    @Test
    fun `a text turn maps to a TextDelta per chunk then Completed, ignoring intermediate events`() {
        val events = mappedEvents(turn(2))

        assertEquals(
            listOf(
                ModelEvent.TextDelta("В"),
                ModelEvent.TextDelta(" Москве"),
                ModelEvent.TextDelta(" сейчас"),
                ModelEvent.TextDelta(" "),
                ModelEvent.TextDelta("14"),
                ModelEvent.TextDelta(":"),
                ModelEvent.TextDelta("32"),
                ModelEvent.TextDelta("."),
                ModelEvent.Completed(Usage(inputTokens = 194, outputTokens = 12)),
            ),
            events,
        )
    }

    @Test
    fun `an unrecognized event name is ignored`() {
        assertEquals(null, RawSseEvent("response.some_future_event", "{}").toModelEvent())
    }

    @Test
    fun `an error event maps to Failed with the server message`() {
        val event = RawSseEvent("error", """{"error":{"message":"boom"}}""")

        assertEquals(ModelEvent.Failed(AgentError.Unexpected("boom")), event.toModelEvent())
    }

    @Test
    fun `a known event whose payload no longer matches the expected shape maps to a protocol error`() {
        val event = RawSseEvent("response.output_text.delta", """{"unexpected":true}""")

        assertEquals(ModelEvent.Failed(AgentError.ProtocolChanged), event.toModelEvent())
    }

    private fun mappedEvents(rawTurnText: String): List<ModelEvent> {
        val parser = SseParser()
        return parser.feed(rawTurnText).mapNotNull { it.toModelEvent() }
    }

    private fun turn(number: Int): String {
        val allTurns = sampleSseFile.readText().split(Regex("(?m)^### turn \\d.*$"))
        // allTurns[0] is empty (text before the first marker), so turn N is at index N.
        return allTurns[number]
    }

    private companion object {
        // Shared with the stage-0 spike so the fixture never drifts from what was actually
        // observed from the backend; see docs/backend-notes.md.
        val sampleSseFile: File = File("../../spike/sample_sse.txt")
    }
}
