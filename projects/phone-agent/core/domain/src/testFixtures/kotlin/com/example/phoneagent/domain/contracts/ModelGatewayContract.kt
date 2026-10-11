package com.example.phoneagent.domain.contracts

import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.conversation.CallId
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.conversation.ItemId
import com.example.phoneagent.domain.conversation.ToolCall
import com.example.phoneagent.domain.model.ModelEvent
import com.example.phoneagent.domain.model.ModelGateway
import com.example.phoneagent.domain.model.ModelRequest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every [ModelGateway] must do, whoever implements it. The scripted fake and the real Responses gateway
 * (driven by a local test server) both run these tests, so the events the agent loop sees from the fake are
 * the events it would see from the real backend. The rules: text arrives in order, a finished answer ends with
 * exactly one Completed, a failure is one Failed and nothing after it, and asking twice is two independent runs.
 */
public abstract class ModelGatewayContract {
    /** A gateway whose backend answers with [chunks] of text and then finishes normally. */
    protected abstract fun replying(chunks: List<String>): ModelGateway

    /** A gateway whose backend asks for [call] and then finishes normally. */
    protected abstract fun callingTool(call: ToolCall): ModelGateway

    /** A gateway whose backend cannot be used and ends the request with [error] (one of [FAILURES]). */
    protected abstract fun failingWith(error: AgentError): ModelGateway

    private val request = ModelRequest(history = listOf(HistoryItem.UserMessage(ItemId("item-1"), "hi")))

    @Test
    public fun `text arrives in order and the answer ends with exactly one Completed`() =
        runTest {
            val events = replying(listOf("Hel", "lo", "!")).stream(request).toList()

            assertEquals(listOf(ModelEvent.TextDelta("Hel"), ModelEvent.TextDelta("lo"), ModelEvent.TextDelta("!")), events.dropLast(1))
            assertTrue("the last event must be Completed: ${events.last()}", events.last() is ModelEvent.Completed)
            assertEquals(1, events.count { it is ModelEvent.Completed })
        }

    @Test
    public fun `a tool call is requested before the answer completes`() =
        runTest {
            val call =
                ToolCall(
                    callId = CallId("call-1"),
                    name = "get_time",
                    arguments = buildJsonObject { put("city", "Moscow") },
                    itemId = ItemId("fc-1"),
                )

            val events = callingTool(call).stream(request).toList()

            assertEquals(ModelEvent.ToolCallRequested(call), events.first())
            assertTrue("the last event must be Completed: ${events.last()}", events.last() is ModelEvent.Completed)
        }

    @Test
    public fun `every failure is a single Failed and nothing follows it`() =
        runTest {
            for (error in FAILURES) {
                val events = failingWith(error).stream(request).toList()

                assertEquals("for $error", listOf<ModelEvent>(ModelEvent.Failed(error)), events)
            }
        }

    @Test
    public fun `asking twice gives two independent runs`() =
        runTest {
            val gateway = replying(listOf("one"))

            val first = gateway.stream(request).toList()
            val second = gateway.stream(request).toList()

            assertEquals(first, second)
        }

    public companion object {
        /** The failures every implementation can be driven into, each with its own recognizable [AgentError]. */
        public val FAILURES: List<AgentError> = listOf(AgentError.Offline, AgentError.SessionExpired, AgentError.Server(500))
    }
}
