package com.example.phoneagent.data.llm.sse

internal data class RawSseEvent(
    val name: String,
    val data: String,
)

/**
 * Incremental text/event-stream tokenizer (WHATWG SSE framing). Feed it network chunks as they
 * arrive; a chunk boundary may fall anywhere, including mid-line, so partial data is buffered
 * until a full line (and eventually a full event) is available.
 */
internal class SseParser {
    private val pending = StringBuilder()
    private var eventName = DEFAULT_EVENT_NAME
    private val dataLines = mutableListOf<String>()

    fun feed(chunk: String): List<RawSseEvent> {
        pending.append(chunk)
        val events = mutableListOf<RawSseEvent>()
        var newline = pending.indexOf("\n")
        while (newline != -1) {
            consumeLine(pending.substring(0, newline).removeSuffix("\r"), events)
            pending.delete(0, newline + 1)
            newline = pending.indexOf("\n")
        }
        return events
    }

    private fun consumeLine(
        line: String,
        events: MutableList<RawSseEvent>,
    ) {
        when {
            line.startsWith(EVENT_PREFIX) -> {
                eventName = line.removePrefix(EVENT_PREFIX).trim()
            }

            line.startsWith(DATA_PREFIX) -> {
                dataLines += line.removePrefix(DATA_PREFIX).removePrefix(" ")
            }

            line.isEmpty() -> {
                if (dataLines.isNotEmpty()) {
                    events += RawSseEvent(eventName, dataLines.joinToString("\n"))
                    dataLines.clear()
                }
                eventName = DEFAULT_EVENT_NAME
            }

            else -> {
                Unit
            }
        }
    }

    private companion object {
        private const val DEFAULT_EVENT_NAME = "message"
        private const val EVENT_PREFIX = "event:"
        private const val DATA_PREFIX = "data:"
    }
}
