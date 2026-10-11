package com.example.phoneagent.domain.agent

import com.example.phoneagent.domain.conversation.CallId
import com.example.phoneagent.domain.conversation.ContextWindowPolicy
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.conversation.ItemId
import com.example.phoneagent.domain.conversation.ToolCall
import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import com.example.phoneagent.domain.fakes.FakeIdGenerator
import com.example.phoneagent.domain.fakes.FakeModelGateway
import com.example.phoneagent.domain.fakes.FakeUserInteraction
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.model.ModelEvent
import com.example.phoneagent.domain.model.ModelGateway
import com.example.phoneagent.domain.model.ModelRequest
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.Tool
import com.example.phoneagent.domain.tools.ToolContext
import com.example.phoneagent.domain.tools.ToolOutcome
import com.example.phoneagent.domain.tools.ToolPipeline
import com.example.phoneagent.domain.tools.ToolRegistry
import com.example.phoneagent.domain.tools.ToolResult
import com.example.phoneagent.domain.tools.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLoopTest {
    private val gateway = FakeModelGateway()
    private val interaction = FakeUserInteraction()
    private val executedCalls = mutableListOf<String>()

    @Test
    fun `text-only reply completes the turn without any tool calls`() =
        runTest {
            gateway.events = listOf(ModelEvent.TextDelta("Hi"), ModelEvent.Completed())

            val events = loop().run(emptyList(), "hello").toList()

            assertEquals(
                listOf(
                    AgentEvent.TextDelta("Hi"),
                    AgentEvent.TurnCompleted(
                        listOf(HistoryItem.UserMessage(ItemId("id-1"), "hello"), HistoryItem.AssistantMessage(ItemId("id-2"), "Hi")),
                    ),
                ),
                events,
            )
        }

    @Test
    fun `a single tool call is executed and its output feeds the next model step`() =
        runTest {
            val call = ToolCall(CallId("call-1"), "get_time", buildJsonObject {}, itemId = ItemId("fc-1"))
            gateway.responseQueue.addLast(listOf(ModelEvent.ToolCallRequested(call), ModelEvent.Completed()))
            gateway.responseQueue.addLast(listOf(ModelEvent.TextDelta("It's noon"), ModelEvent.Completed()))
            val registry = ToolRegistry(setOf(fakeTool("get_time") { ToolResult.Success("noon") }))

            val events = loop(registry).run(emptyList(), "what time is it").toList()

            assertEquals(listOf("get_time"), executedCalls)
            assertEquals(
                listOf(
                    AgentEvent.ToolCallStarted(call, CallDescription("get_time", "calling get_time")),
                    AgentEvent.ToolCallFinished(call, ToolOutcome.Success("noon")),
                    AgentEvent.TextDelta("It's noon"),
                    AgentEvent.TurnCompleted(
                        listOf(
                            HistoryItem.UserMessage(ItemId("id-1"), "what time is it"),
                            HistoryItem.ToolCallItem(ItemId("fc-1"), CallId("call-1"), "get_time", buildJsonObject {}),
                            HistoryItem.ToolOutput(ItemId("id-2"), CallId("call-1"), "noon"),
                            HistoryItem.AssistantMessage(ItemId("id-3"), "It's noon"),
                        ),
                    ),
                ),
                events,
            )
            assertEquals(2, gateway.requests.size)
            val secondRequestHistory = gateway.requests[1].history
            assertTrue(secondRequestHistory.any { it is HistoryItem.ToolOutput && it.output == "noon" })
        }

    @Test
    fun `a failing tool does not abort the turn — its failure is fed back to the model`() =
        runTest {
            val call = ToolCall(CallId("call-1"), "get_time", buildJsonObject {})
            gateway.responseQueue.addLast(listOf(ModelEvent.ToolCallRequested(call), ModelEvent.Completed()))
            gateway.responseQueue.addLast(listOf(ModelEvent.TextDelta("sorry, failed"), ModelEvent.Completed()))
            val registry = ToolRegistry(setOf(fakeTool("get_time") { ToolResult.Failed("network down") }))

            val events = loop(registry).run(emptyList(), "what time is it").toList()

            val finished = events.filterIsInstance<AgentEvent.ToolCallFinished>().single()
            assertEquals(ToolOutcome.Failed("network down"), finished.outcome)
            assertTrue(events.last() is AgentEvent.TurnCompleted)
        }

    @Test
    fun `an unknown tool name does not abort the turn either`() =
        runTest {
            val call = ToolCall(CallId("call-1"), "nonexistent", buildJsonObject {})
            gateway.responseQueue.addLast(listOf(ModelEvent.ToolCallRequested(call), ModelEvent.Completed()))
            gateway.responseQueue.addLast(listOf(ModelEvent.TextDelta("I can't do that"), ModelEvent.Completed()))

            val events = loop(ToolRegistry(emptySet())).run(emptyList(), "do the thing").toList()

            val finished = events.filterIsInstance<AgentEvent.ToolCallFinished>().single()
            assertEquals(ToolOutcome.Failed("unknown tool: nonexistent"), finished.outcome)
            assertTrue(events.last() is AgentEvent.TurnCompleted)
        }

    @Test
    fun `several tool calls in one response are executed in order before the next model step`() =
        runTest {
            val callA = ToolCall(CallId("call-a"), "tool_a", buildJsonObject {})
            val callB = ToolCall(CallId("call-b"), "tool_b", buildJsonObject {})
            gateway.responseQueue.addLast(
                listOf(ModelEvent.ToolCallRequested(callA), ModelEvent.ToolCallRequested(callB), ModelEvent.Completed()),
            )
            gateway.responseQueue.addLast(listOf(ModelEvent.TextDelta("done"), ModelEvent.Completed()))
            val registry =
                ToolRegistry(
                    setOf(
                        fakeTool("tool_a") { ToolResult.Success("a-done") },
                        fakeTool("tool_b") { ToolResult.Success("b-done") },
                    ),
                )

            loop(registry).run(emptyList(), "do both").toList()

            assertEquals(listOf("tool_a", "tool_b"), executedCalls)
        }

    @Test
    fun `a step within the tool call limit runs exactly as before`() =
        runTest {
            val callA = ToolCall(CallId("call-a"), "tool_a", buildJsonObject {})
            val callB = ToolCall(CallId("call-b"), "tool_b", buildJsonObject {})
            gateway.responseQueue.addLast(
                listOf(ModelEvent.ToolCallRequested(callA), ModelEvent.ToolCallRequested(callB), ModelEvent.Completed()),
            )
            gateway.responseQueue.addLast(listOf(ModelEvent.TextDelta("done"), ModelEvent.Completed()))
            val registry =
                ToolRegistry(
                    setOf(
                        fakeTool("tool_a") { ToolResult.Success("a-done") },
                        fakeTool("tool_b") { ToolResult.Success("b-done") },
                    ),
                )
            val config = AgentConfig(maxToolCallsPerTurn = 2)

            val events = loop(registry, config).run(emptyList(), "do both").toList()

            assertEquals(listOf("tool_a", "tool_b"), executedCalls)
            assertTrue(events.last() is AgentEvent.TurnCompleted)
        }

    @Test
    fun `a step requesting more calls than the limit allows runs none and fails with StepLimit`() =
        runTest {
            val callA = ToolCall(CallId("call-a"), "tool_a", buildJsonObject {})
            val callB = ToolCall(CallId("call-b"), "tool_b", buildJsonObject {})
            val callC = ToolCall(CallId("call-c"), "tool_c", buildJsonObject {})
            gateway.responseQueue.addLast(
                listOf(
                    ModelEvent.ToolCallRequested(callA),
                    ModelEvent.ToolCallRequested(callB),
                    ModelEvent.ToolCallRequested(callC),
                    ModelEvent.Completed(),
                ),
            )
            val registry =
                ToolRegistry(
                    setOf(
                        fakeTool("tool_a") { ToolResult.Success("a-done") },
                        fakeTool("tool_b") { ToolResult.Success("b-done") },
                        fakeTool("tool_c") { ToolResult.Success("c-done") },
                    ),
                )
            val config = AgentConfig(maxToolCallsPerTurn = 2)

            val events = loop(registry, config).run(emptyList(), "do three").toList()

            assertTrue("no tool may run: $executedCalls", executedCalls.isEmpty())
            val failure = events.last() as AgentEvent.TurnFailed
            assertEquals(AgentError.StepLimit, failure.error)
            assertTrue(
                "only the user message may be in history, was ${failure.newItems}",
                failure.newItems.all { it is HistoryItem.UserMessage },
            )
        }

    @Test
    fun `the user declining a CONFIRM tool rejects it without executing, and the turn continues`() =
        runTest {
            interaction.approvalResult = false
            val call = ToolCall(CallId("call-1"), "set_alarm", buildJsonObject {})
            gateway.responseQueue.addLast(listOf(ModelEvent.ToolCallRequested(call), ModelEvent.Completed()))
            gateway.responseQueue.addLast(listOf(ModelEvent.TextDelta("ok, not setting it"), ModelEvent.Completed()))
            val registry =
                ToolRegistry(setOf(fakeTool("set_alarm", risk = Risk.CONFIRM) { ToolResult.Success("set") }))

            val events = loop(registry).run(emptyList(), "set an alarm").toList()

            val expectedRequest = ApprovalRequest(CallDescription("set_alarm", "calling set_alarm"), Risk.CONFIRM, buildJsonObject {})
            assertTrue(events.contains(AgentEvent.ApprovalRequested(expectedRequest)))
            val finished = events.filterIsInstance<AgentEvent.ToolCallFinished>().single()
            assertEquals(ToolOutcome.Rejected("user declined"), finished.outcome)
            assertTrue(executedCalls.isEmpty())
        }

    @Test
    fun `a single approval resolution reports its exact call id`() =
        runTest {
            val call = ToolCall(CallId("call-1"), "set_alarm", buildJsonObject {})
            gateway.responseQueue.addLast(listOf(ModelEvent.ToolCallRequested(call), ModelEvent.Completed()))
            gateway.responseQueue.addLast(listOf(ModelEvent.TextDelta("done"), ModelEvent.Completed()))
            val registry = ToolRegistry(setOf(fakeTool("set_alarm", risk = Risk.CONFIRM) { ToolResult.Success("set") }))
            val resolvedCallIds = mutableListOf<List<CallId>>()

            loop(registry).run(emptyList(), "set an alarm") { callIds -> resolvedCallIds += callIds }.toList()

            assertEquals(listOf(listOf(call.callId)), resolvedCallIds)
        }

    @Test
    fun `a model that keeps requesting tools forever is stopped at the step limit`() =
        runTest {
            val call = ToolCall(CallId("call-1"), "get_time", buildJsonObject {})
            gateway.events = listOf(ModelEvent.ToolCallRequested(call), ModelEvent.Completed())
            val registry = ToolRegistry(setOf(fakeTool("get_time") { ToolResult.Success("noon") }))

            val events = loop(registry, AgentConfig(maxSteps = 3)).run(emptyList(), "loop forever").toList()

            val failed = events.last() as AgentEvent.TurnFailed
            assertEquals(AgentError.StepLimit, failed.error)
            assertEquals(HistoryItem.UserMessage(ItemId("id-1"), "loop forever"), failed.newItems.first())
            assertEquals(3, executedCalls.size)
        }

    @Test
    fun `a model error mid-stream ends the turn but keeps the user message for history`() =
        runTest {
            gateway.events = listOf(ModelEvent.TextDelta("partial"), ModelEvent.Failed(AgentError.Offline))

            val events = loop().run(emptyList(), "hello").toList()

            assertEquals(
                listOf(
                    AgentEvent.TextDelta("partial"),
                    AgentEvent.TurnFailed(AgentError.Offline, listOf(HistoryItem.UserMessage(ItemId("id-1"), "hello"))),
                ),
                events,
            )
        }

    @Test
    fun `cancelling the collector stops the loop instead of hanging`() =
        runTest {
            val hangingGateway =
                object : ModelGateway {
                    override fun stream(request: ModelRequest): Flow<ModelEvent> =
                        flow {
                            emit(ModelEvent.TextDelta("partial"))
                            awaitCancellation()
                        }
                }
            val agentLoop =
                AgentLoop(
                    modelGateway = hangingGateway,
                    toolPipeline = pipeline(ToolRegistry(emptySet())),
                    contextWindowPolicy = ContextWindowPolicy { it },
                    idGenerator = FakeIdGenerator(),
                )

            withContext(Dispatchers.Default) {
                withTimeout(5_000) {
                    val collecting = async { agentLoop.run(emptyList(), "hello").toList() }
                    delay(50) // let the first delta actually emit before cancelling
                    collecting.cancel()
                    collecting.join()
                }
            }
        }

    private fun loop(
        registry: ToolRegistry = ToolRegistry(emptySet()),
        config: AgentConfig = AgentConfig(),
    ): AgentLoop =
        AgentLoop(
            modelGateway = gateway,
            toolPipeline = pipeline(registry),
            contextWindowPolicy = ContextWindowPolicy { it },
            idGenerator = FakeIdGenerator(),
            config = config,
        )

    private fun pipeline(registry: ToolRegistry): ToolPipeline =
        ToolPipeline(
            registry = registry,
            userInteraction = interaction,
            clock = FakeClock(),
            logger = FakeAppLogger(),
        )

    @Test
    fun `describing a call uses the description of the registered tool`() {
        val registry = ToolRegistry(setOf(fakeTool("get_time") { ToolResult.Success("noon") }))
        val call = ToolCall(CallId("call-1"), "get_time", buildJsonObject {})

        assertEquals(CallDescription("get_time", "calling get_time"), loop(registry).describe(call))
    }

    @Test
    fun `describing a call to an unknown tool falls back to its name`() {
        val call = ToolCall(CallId("call-1"), "vanished_tool", buildJsonObject {})

        assertEquals(CallDescription(title = "vanished_tool", summary = "Unknown tool"), loop().describe(call))
    }

    @Test
    fun `the tool call limit counts calls across the steps of one turn, not per step`() =
        runTest {
            fun step(vararg names: String) =
                listOf(
                    *names
                        .map {
                            ModelEvent.ToolCallRequested(ToolCall(CallId("call-$it-${gateway.responseQueue.size}"), it, buildJsonObject {}))
                        }.toTypedArray(),
                    ModelEvent.Completed(),
                )
            gateway.responseQueue.addLast(step("tool_a", "tool_b"))
            gateway.responseQueue.addLast(step("tool_a", "tool_b"))
            val registry =
                ToolRegistry(
                    setOf(
                        fakeTool("tool_a") { ToolResult.Success("a-done") },
                        fakeTool("tool_b") { ToolResult.Success("b-done") },
                    ),
                )

            val events = loop(registry, AgentConfig(maxToolCallsPerTurn = 3)).run(emptyList(), "twice").toList()

            assertEquals("only the first step fits into the budget of 3: $executedCalls", listOf("tool_a", "tool_b"), executedCalls)
            val failure = events.last() as AgentEvent.TurnFailed
            assertEquals(AgentError.StepLimit, failure.error)
            assertEquals(2, failure.newItems.count { it is HistoryItem.ToolCallItem })
        }

    @Test
    fun `a tool call limit below one is rejected when the config is built`() {
        for (invalid in listOf(0, -1)) {
            val error = runCatching { AgentConfig(maxToolCallsPerTurn = invalid) }.exceptionOrNull()

            assertTrue("limit $invalid must be rejected, got $error", error is IllegalArgumentException)
        }
        assertEquals(1, AgentConfig(maxToolCallsPerTurn = 1).maxToolCallsPerTurn)
    }

    private fun fakeTool(
        name: String,
        risk: Risk = Risk.SAFE,
        execute: suspend () -> ToolResult,
    ): Tool =
        object : Tool {
            override val spec = ToolSpec(name, "does $name", buildJsonObject {}, risk)

            override fun describe(args: JsonObject): CallDescription = CallDescription(name, "calling $name")

            override suspend fun execute(
                args: JsonObject,
                context: ToolContext,
            ): ToolResult {
                executedCalls += name
                return execute()
            }
        }
}
