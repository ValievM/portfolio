package com.example.phoneagent.domain.agent

import com.example.phoneagent.domain.conversation.CallId
import com.example.phoneagent.domain.conversation.ContextWindowPolicy
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.conversation.ItemId
import com.example.phoneagent.domain.conversation.LastNMessagesPolicy
import com.example.phoneagent.domain.conversation.ToolCall
import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import com.example.phoneagent.domain.fakes.FakeIdGenerator
import com.example.phoneagent.domain.fakes.FakeModelGateway
import com.example.phoneagent.domain.fakes.FakeUserInteraction
import com.example.phoneagent.domain.model.ModelEvent
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.Tool
import com.example.phoneagent.domain.tools.ToolContext
import com.example.phoneagent.domain.tools.ToolPipeline
import com.example.phoneagent.domain.tools.ToolRegistry
import com.example.phoneagent.domain.tools.ToolResult
import com.example.phoneagent.domain.tools.ToolSpec
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End to end through [AgentLoop]: whatever the context window policy cuts, a
 * [HistoryItem.ToolOutput] in the request always has its [HistoryItem.ToolCallItem] (and vice
 * versa), and the window starts at a user message.
 */
class AgentLoopContextWindowTest {
    @Test
    fun `no request ever carries an orphaned tool output or call`() =
        runTest {
            val gateway = FakeModelGateway()
            val registry = ToolRegistry(setOf(fakeTool("tool") { ToolResult.Success("done") }))
            val loop =
                AgentLoop(
                    modelGateway = gateway,
                    toolPipeline = pipeline(registry),
                    contextWindowPolicy = LastNMessagesPolicy(maxItems = 6),
                    idGenerator = FakeIdGenerator(),
                )

            // Turn 1: one tool call. Turn 2: another tool call. Turn 3: plain text.
            gateway.responseQueue.addLast(listOf(ModelEvent.ToolCallRequested(call("c1")), ModelEvent.Completed()))
            val turn1 = loop.run(emptyList(), "q1").toList()
            val historyAfterTurn1 = (turn1.last() as AgentEvent.TurnCompleted).newItems

            gateway.responseQueue.addLast(listOf(ModelEvent.ToolCallRequested(call("c2")), ModelEvent.Completed()))
            val turn2 = loop.run(historyAfterTurn1, "q2").toList()
            val historyAfterTurn2 = historyAfterTurn1 + (turn2.last() as AgentEvent.TurnCompleted).newItems

            gateway.responseQueue.addLast(listOf(ModelEvent.TextDelta("done"), ModelEvent.Completed()))
            loop.run(historyAfterTurn2, "q3").toList()

            for (request in gateway.requests) {
                val calls = request.history.filterIsInstance<HistoryItem.ToolCallItem>().map { it.callId }
                val outputs = request.history.filterIsInstance<HistoryItem.ToolOutput>().map { it.callId }
                assertTrue(
                    "request ${request.history} has outputs without their calls",
                    outputs.all { it in calls },
                )
                assertTrue(
                    "request ${request.history} has calls without their outputs",
                    calls.all { it in outputs },
                )
                assertTrue(
                    "request must start at a user message, was ${request.history.first()::class.simpleName}",
                    request.history.first() is HistoryItem.UserMessage,
                )
            }
            // The last request was actually cut by the small window (not everything fit).
            val last = gateway.requests.last().history
            assertTrue(
                "the small window must have dropped older turns",
                last.none { it is HistoryItem.UserMessage && it.text == "q1" },
            )
            assertEquals(
                "the newest turn must be whole",
                listOf("q2", "c2", "out-c2", "q3"),
                last.map { item ->
                    when (item) {
                        is HistoryItem.UserMessage -> item.text
                        is HistoryItem.ToolCallItem -> item.callId.value
                        is HistoryItem.ToolOutput -> "out-${item.callId.value}"
                        else -> "?"
                    }
                },
            )
        }

    private fun call(id: String) = ToolCall(CallId(id), "tool", buildJsonObject {})

    private fun fakeTool(
        name: String,
        result: suspend () -> ToolResult,
    ): Tool =
        object : Tool {
            override val spec = ToolSpec(name, "does $name", buildJsonObject {}, Risk.SAFE)

            override fun describe(args: kotlinx.serialization.json.JsonObject) =
                com.example.phoneagent.domain.tools
                    .CallDescription(name, "calling $name")

            override suspend fun execute(
                args: kotlinx.serialization.json.JsonObject,
                context: ToolContext,
            ): ToolResult = result()
        }

    private fun pipeline(registry: ToolRegistry): ToolPipeline =
        ToolPipeline(
            registry = registry,
            userInteraction = FakeUserInteraction(),
            clock = FakeClock(),
            logger = FakeAppLogger(),
        )
}
