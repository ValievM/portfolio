package com.example.phoneagent.data.llm.sse

import org.junit.Assert.assertEquals
import org.junit.Test

class SseParserTest {
    @Test
    fun `parses a single complete event`() {
        val parser = SseParser()

        val events = parser.feed("event: response.completed\ndata: {\"a\":1}\n\n")

        assertEquals(listOf(RawSseEvent("response.completed", "{\"a\":1}")), events)
    }

    @Test
    fun `an event split across two chunks is only emitted once both arrive`() {
        val parser = SseParser()

        val fromFirstChunk = parser.feed("event: response.output_text.d")
        val fromSecondChunk = parser.feed("elta\ndata: {\"delta\":\"hi\"}\n\n")

        assertEquals(emptyList<RawSseEvent>(), fromFirstChunk)
        assertEquals(listOf(RawSseEvent("response.output_text.delta", "{\"delta\":\"hi\"}")), fromSecondChunk)
    }

    @Test
    fun `multiple data lines in one event are joined with newlines`() {
        val parser = SseParser()

        val events = parser.feed("event: message\ndata: line one\ndata: line two\n\n")

        assertEquals(listOf(RawSseEvent("message", "line one\nline two")), events)
    }

    @Test
    fun `lines that are not event, data or blank are ignored`() {
        val parser = SseParser()

        val events = parser.feed("### turn 1\nevent: response.completed\ndata: {}\n\n")

        assertEquals(listOf(RawSseEvent("response.completed", "{}")), events)
    }

    @Test
    fun `two events in one chunk are both returned in order`() {
        val parser = SseParser()

        val events =
            parser.feed(
                "event: response.created\ndata: {}\n\n" +
                    "event: response.completed\ndata: {}\n\n",
            )

        assertEquals(
            listOf(
                RawSseEvent("response.created", "{}"),
                RawSseEvent("response.completed", "{}"),
            ),
            events,
        )
    }

    @Test
    fun `data without a preceding event name defaults to message`() {
        val parser = SseParser()

        val events = parser.feed("data: {}\n\n")

        assertEquals(listOf(RawSseEvent("message", "{}")), events)
    }
}
