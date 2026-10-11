package com.example.phoneagent.domain.conversation

import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The window is built from whole turns: a turn is everything from a [HistoryItem.UserMessage] up
 * to (not including) the next one. A paired [HistoryItem.ToolCallItem] and [HistoryItem.ToolOutput]
 * must never end up on different sides of the window boundary, and the window always starts at a
 * user message. U = question, A = assistant answer, C = tool call, O = tool output, X = opaque.
 */
class LastNMessagesPolicyTest {
    @Test
    fun `history shorter than the limit is returned unchanged`() {
        val policy = LastNMessagesPolicy(maxItems = 3)
        val history = listOf(u("1"), u("2"))

        assertEquals(history, policy.apply(history))
    }

    @Test
    fun `maxItems must be positive`() {
        assertThrows(IllegalArgumentException::class.java) { LastNMessagesPolicy(maxItems = 0) }
    }

    @Test
    fun `three turns of four items with limit six keep only the newest turn whole`() {
        val policy = LastNMessagesPolicy(maxItems = 6)
        val history = turn("t1") + turn("t2") + turn("t3")

        assertEquals(turn("t3"), policy.apply(history))
    }

    @Test
    fun `three turns of four items with limit eight keep the two newest turns`() {
        val policy = LastNMessagesPolicy(maxItems = 8)
        val history = turn("t1") + turn("t2") + turn("t3")

        assertEquals(turn("t2") + turn("t3"), policy.apply(history))
    }

    @Test
    fun `every window size keeps call and output pairs together and starts at a user message`() {
        val history =
            turn("t1") +
                turn("t2", parallelCalls = 2) +
                listOf(opaque("x1")) +
                turn("t3")

        for (maxItems in 1..history.size) {
            val window = LastNMessagesPolicy(maxItems).apply(history)

            assertTrue("window at maxItems=$maxItems is empty", window.isNotEmpty())
            assertTrue(
                "window at maxItems=$maxItems must start at a user message, was ${window.first()::class.simpleName}",
                window.first() is HistoryItem.UserMessage,
            )
            val callIds = window.filterIsInstance<HistoryItem.ToolCallItem>().map { it.callId }
            val outputIds = window.filterIsInstance<HistoryItem.ToolOutput>().map { it.callId }
            for (output in outputIds) {
                assertTrue("output $output at maxItems=$maxItems lost its call", output in callIds)
            }
            for (call in callIds) {
                assertTrue("call $call at maxItems=$maxItems lost its output", call in outputIds)
            }
        }
    }

    @Test
    fun `parallel calls of one step stay together or are dropped together`() {
        val policy = LastNMessagesPolicy(maxItems = 9)
        val older = turn("t1", parallelCalls = 3)
        val newest = turn("t2", withTools = false)
        val history = older + newest

        val window = policy.apply(history)

        assertEquals(newest, window)
        val calls = older.filterIsInstance<HistoryItem.ToolCallItem>()
        for (call in calls) {
            assertTrue(
                "parallel call ${call.callId.value} must be all-or-nothing",
                window.none { it is HistoryItem.ToolCallItem && it.callId == call.callId },
            )
        }
    }

    @Test
    fun `a single turn longer than the limit is returned whole`() {
        val policy = LastNMessagesPolicy(maxItems = 4)
        val history = turn("t1", parallelCalls = 4) // 1 user + 4*2 + 1 assistant = 10 items

        assertEquals(history, policy.apply(history))
    }

    @Test
    fun `empty history gives an empty window`() {
        assertEquals(emptyList<HistoryItem>(), LastNMessagesPolicy(maxItems = 5).apply(emptyList()))
    }

    @Test
    fun `history without a user message is one group returned whole`() {
        val history = listOf(a("a1"), opaque("x1"), a("a2"))

        assertEquals(history, LastNMessagesPolicy(maxItems = 2).apply(history))
    }

    @Test
    fun `an opaque item between the output and the next question stays in its turn`() {
        val policy = LastNMessagesPolicy(maxItems = 10)
        val history = turn("t1") + listOf(opaque("x1")) + turn("t2")

        val window = policy.apply(history)

        assertTrue("the opaque item must stay inside the first turn", window.contains(opaque("x1")))
    }

    @Test
    fun `a non fitting middle turn stops the window even if an older one would fit`() {
        // Sizes: newest 6, middle 4, oldest 2, limit 8. Correct: the middle does not fit (6+4>8)
        // and the window stops there - the oldest turn is NOT jumped to (that would tear a hole).
        val policy = LastNMessagesPolicy(maxItems = 8)
        val history = turn("t1", withTools = false) + turn("t2") + turn("t3", parallelCalls = 2)

        val window = policy.apply(history)

        assertEquals(turn("t3", parallelCalls = 2), window)
    }

    @Test
    fun `the window is a sublist of the original history preserving order`() {
        val history = turn("t1") + turn("t2") + turn("t3")
        val policy = LastNMessagesPolicy(maxItems = 8)

        val window = policy.apply(history)

        val indexOfFirst = history.indexOf(window.first())
        val indexOfLast = history.indexOf(window.last())
        assertEquals(window, history.subList(indexOfFirst, indexOfLast + 1))
    }

    // -- helpers -------------------------------------------------------------

    /** One turn: U, then [parallelCalls] pairs of C+O, then A. */
    private fun turn(
        name: String,
        withTools: Boolean = true,
        parallelCalls: Int = 1,
    ): List<HistoryItem> =
        buildList {
            add(u("$name-u"))
            if (withTools) {
                repeat(parallelCalls) { index ->
                    add(c("$name-c$index"))
                    add(o("$name-c$index"))
                }
            }
            add(a("$name-a"))
        }

    private fun u(text: String): HistoryItem = HistoryItem.UserMessage(ItemId(text), text)

    private fun a(text: String): HistoryItem = HistoryItem.AssistantMessage(ItemId(text), text)

    private fun c(callId: String): HistoryItem =
        HistoryItem.ToolCallItem(ItemId("item-$callId"), CallId(callId), "tool", buildJsonObject {})

    private fun o(callId: String): HistoryItem = HistoryItem.ToolOutput(ItemId("out-$callId"), CallId(callId), "output")

    private fun opaque(id: String): HistoryItem = HistoryItem.Opaque(ItemId(id), "provider", buildJsonObject {})
}
