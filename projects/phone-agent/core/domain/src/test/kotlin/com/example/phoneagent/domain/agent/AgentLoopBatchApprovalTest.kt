package com.example.phoneagent.domain.agent

import com.example.phoneagent.domain.conversation.CallId
import com.example.phoneagent.domain.conversation.ContextWindowPolicy
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.conversation.ToolCall
import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import com.example.phoneagent.domain.fakes.FakeIdGenerator
import com.example.phoneagent.domain.fakes.FakeModelGateway
import com.example.phoneagent.domain.fakes.FakeUserInteraction
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.interaction.Capability
import com.example.phoneagent.domain.interaction.CapabilityResult
import com.example.phoneagent.domain.interaction.UserInteraction
import com.example.phoneagent.domain.model.ModelEvent
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.Tool
import com.example.phoneagent.domain.tools.ToolContext
import com.example.phoneagent.domain.tools.ToolOutcome
import com.example.phoneagent.domain.tools.ToolPipeline
import com.example.phoneagent.domain.tools.ToolRegistry
import com.example.phoneagent.domain.tools.ToolResult
import com.example.phoneagent.domain.tools.ToolSpec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLoopBatchApprovalTest {
    private val gateway = FakeModelGateway()
    private val interaction = FakeUserInteraction()
    private val executed = mutableListOf<String>()

    @Test
    fun `several confirm calls in one response are approved with a single question listing them in order`() =
        runTest {
            respondWith(alarm(1), alarm(2), alarm(3))

            val events = loop(tools(alarmTool())).run(emptyList(), "три будильника").toList()

            assertEquals(1, interaction.batchRequests.size)
            assertEquals(
                listOf("set_alarm #1", "set_alarm #2", "set_alarm #3"),
                interaction.batchRequests.single().map { it.description.summary },
            )
            assertTrue(interaction.approvalRequests.isEmpty())
            assertEquals(listOf("set_alarm #1", "set_alarm #2", "set_alarm #3"), executed)
            val batchIndex = events.indexOfFirst { it is AgentEvent.BatchApprovalRequested }
            assertEquals(
                AgentEvent.BatchApprovalRequested(listOf(CallId("call-1"), CallId("call-2"), CallId("call-3"))),
                events[batchIndex],
            )
            // All cards exist before the question, and nothing has run yet.
            assertEquals(3, events.take(batchIndex).count { it is AgentEvent.ToolCallStarted })
            assertTrue(events.take(batchIndex).none { it is AgentEvent.ToolCallFinished })
            val outputs = (events.last() as AgentEvent.TurnCompleted).newItems.filterIsInstance<HistoryItem.ToolOutput>()
            assertEquals(listOf(CallId("call-1"), CallId("call-2"), CallId("call-3")), outputs.map { it.callId })
        }

    @Test
    fun `a batch approval resolution reports the exact ordered call ids`() =
        runTest {
            respondWith(alarm(1), alarm(2), alarm(3))
            val resolvedCallIds = mutableListOf<List<CallId>>()

            loop(tools(alarmTool())).run(emptyList(), "три будильника") { callIds -> resolvedCallIds += callIds }.toList()

            assertEquals(listOf(listOf(CallId("call-1"), CallId("call-2"), CallId("call-3"))), resolvedCallIds)
        }

    @Test
    fun `declining the shared question rejects every call and the turn continues`() =
        runTest {
            interaction.batchApprovalResult = false
            respondWith(alarm(1), alarm(2))

            val events = loop(tools(alarmTool())).run(emptyList(), "два будильника").toList()

            assertTrue(executed.isEmpty())
            val outcomes = events.filterIsInstance<AgentEvent.ToolCallFinished>().map { it.outcome }
            assertEquals(listOf(ToolOutcome.Rejected("user declined"), ToolOutcome.Rejected("user declined")), outcomes)
            assertTrue(events.last() is AgentEvent.TurnCompleted)
        }

    @Test
    fun `a broken batch description is not presented as a user decline or approval request`() =
        runTest {
            respondWith(alarm(1), alarm(2))

            val events = loop(tools(brokenDescribeAlarmTool())).run(emptyList(), "два будильника").toList()

            assertTrue(executed.isEmpty())
            assertTrue(interaction.batchRequests.isEmpty())
            // These are the only events that move a card into its awaiting-approval state.
            assertTrue(events.none { it is AgentEvent.BatchApprovalRequested || it is AgentEvent.ApprovalRequested })
        }

    @Test
    fun `a broken batch description tells the model the calls were not shown rather than declined`() =
        runTest {
            respondWith(alarm(1), alarm(2))

            loop(tools(brokenDescribeAlarmTool())).run(emptyList(), "два будильника").toList()

            val outputs =
                gateway.requests
                    .last()
                    .history
                    .filterIsInstance<HistoryItem.ToolOutput>()
            assertEquals(2, outputs.size)
            assertEquals(
                listOf(
                    "Rejected: not shown to user: one action description could not be built; no calls in the batch were run",
                    "Rejected: not shown to user: one action description could not be built; no calls in the batch were run",
                ),
                outputs.map { it.output },
            )
        }

    @Test
    fun `a safe call runs without a question and only the confirm calls are asked together`() =
        runTest {
            respondWith(call(1, "find_files"), alarm(2), alarm(3))

            loop(tools(alarmTool(), safeTool())).run(emptyList(), "найди и поставь").toList()

            assertEquals(1, interaction.batchRequests.size)
            assertEquals(2, interaction.batchRequests.single().size)
            assertTrue(interaction.approvalRequests.isEmpty())
            assertEquals(listOf("find_files #1", "set_alarm #2", "set_alarm #3"), executed)
        }

    @Test
    fun `sensitive calls are never batched and are confirmed one by one`() =
        runTest {
            respondWith(call(1, "send_sms"), call(2, "send_sms"))

            loop(tools(sensitiveTool())).run(emptyList(), "два сообщения").toList()

            assertTrue(interaction.batchRequests.isEmpty())
            assertEquals(2, interaction.approvalRequests.size)
            assertEquals(listOf(Risk.SENSITIVE, Risk.SENSITIVE), interaction.approvalRequests.map { it.risk })
        }

    @Test
    fun `a lone sensitive call uses a single confirmation and never a one-call batch`() =
        runTest {
            respondWith(call(1, "send_sms"))

            loop(tools(sensitiveTool())).run(emptyList(), "отправь сообщение").toList()

            assertTrue(interaction.batchRequests.isEmpty())
            assertEquals(listOf(Risk.SENSITIVE), interaction.approvalRequests.map { it.risk })
        }

    @Test
    fun `mixed confirm and sensitive calls execute in model order with sensitive confirmed separately`() =
        runTest {
            respondWith(alarm(1), call(2, "send_sms"), alarm(3))

            loop(tools(alarmTool(), sensitiveTool())).run(emptyList(), "выполни три действия").toList()

            assertEquals(listOf("set_alarm #1", "set_alarm #3"), interaction.batchRequests.single().map { it.description.summary })
            assertEquals(listOf(Risk.SENSITIVE), interaction.approvalRequests.map { it.risk })
            assertEquals(listOf("set_alarm #1", "send_sms #2", "set_alarm #3"), executed)
        }

    @Test
    fun `a single confirm call keeps the ordinary one-call confirmation`() =
        runTest {
            respondWith(alarm(1))

            val events = loop(tools(alarmTool())).run(emptyList(), "один будильник").toList()

            assertTrue(interaction.batchRequests.isEmpty())
            assertEquals(1, interaction.approvalRequests.size)
            assertTrue(events.any { it is AgentEvent.ApprovalRequested })
            assertTrue(events.none { it is AgentEvent.BatchApprovalRequested })
        }

    @Test
    fun `a call with invalid arguments is not shown in the question and fails on its own`() =
        runTest {
            respondWith(alarm(1), ToolCall(CallId("call-bad"), "set_alarm", buildJsonObject {}), alarm(2))

            val events = loop(tools(alarmTool(required = listOf("n")))).run(emptyList(), "три").toList()

            assertEquals(listOf("set_alarm #1", "set_alarm #2"), interaction.batchRequests.single().map { it.description.summary })
            val bad = events.filterIsInstance<AgentEvent.ToolCallFinished>().single { it.call.callId == CallId("call-bad") }
            assertEquals(ToolOutcome.Failed("missing required fields: n"), bad.outcome)
            assertEquals(listOf("set_alarm #1", "set_alarm #2"), executed)
        }

    @Test
    fun `calls over the batch limit are asked in further groups and a lone rest gets the ordinary question`() =
        runTest {
            respondWith(alarm(1), alarm(2), alarm(3), alarm(4), alarm(5))

            loop(tools(alarmTool()), maxBatchSize = 2).run(emptyList(), "пять").toList()

            assertEquals(listOf(2, 2), interaction.batchRequests.map { it.size })
            assertEquals(1, interaction.approvalRequests.size)
            assertEquals(5, executed.size)
        }

    @Test
    fun `cancelling while the shared question is open executes nothing`() =
        runTest {
            val asked = CompletableDeferred<Unit>()
            val hanging =
                object : UserInteraction {
                    override suspend fun confirm(request: ApprovalRequest): Boolean = awaitCancellation()

                    override suspend fun confirmBatch(requests: List<ApprovalRequest>): Boolean {
                        asked.complete(Unit)
                        awaitCancellation()
                    }

                    override suspend fun ensure(capability: Capability): CapabilityResult = CapabilityResult.Available
                }
            respondWith(alarm(1), alarm(2))

            val job = launch { loop(tools(alarmTool()), userInteraction = hanging).run(emptyList(), "два").toList() }
            asked.await()
            job.cancel()
            advanceUntilIdle()

            assertTrue(job.isCancelled)
            assertTrue(executed.isEmpty())
        }

    private fun respondWith(vararg calls: ToolCall) {
        gateway.responseQueue.addLast(calls.map { ModelEvent.ToolCallRequested(it) } + ModelEvent.Completed())
        gateway.responseQueue.addLast(listOf(ModelEvent.TextDelta("готово"), ModelEvent.Completed()))
    }

    private fun alarm(n: Int) = call(n, "set_alarm")

    private fun call(
        n: Int,
        name: String,
    ) = ToolCall(CallId("call-$n"), name, buildJsonObject { put("n", n) })

    private fun tools(vararg tools: Tool) = ToolRegistry(tools.toSet())

    private fun alarmTool(required: List<String> = emptyList()) = fakeTool("set_alarm", Risk.CONFIRM, required)

    private fun safeTool() = fakeTool("find_files", Risk.SAFE)

    private fun sensitiveTool() = fakeTool("send_sms", Risk.SENSITIVE)

    private fun brokenDescribeAlarmTool(): Tool =
        object : Tool {
            override val spec =
                ToolSpec(
                    "set_alarm",
                    "does set_alarm",
                    buildJsonObject { put("type", "object") },
                    Risk.CONFIRM,
                )

            override fun describe(args: JsonObject): CallDescription {
                if (args.number() == "1") error("broken description")
                return CallDescription("set_alarm", "set_alarm #${args.number()}")
            }

            override suspend fun execute(
                args: JsonObject,
                context: ToolContext,
            ): ToolResult {
                executed += "set_alarm #${args.number()}"
                return ToolResult.Success("set_alarm done")
            }
        }

    private fun loop(
        registry: ToolRegistry,
        maxBatchSize: Int = 15,
        userInteraction: UserInteraction = interaction,
    ): AgentLoop =
        AgentLoop(
            modelGateway = gateway,
            toolPipeline =
                ToolPipeline(
                    registry = registry,
                    userInteraction = userInteraction,
                    clock = FakeClock(),
                    logger = FakeAppLogger(),
                    maxBatchSize = maxBatchSize,
                ),
            contextWindowPolicy = ContextWindowPolicy { it },
            idGenerator = FakeIdGenerator(),
        )

    private fun fakeTool(
        name: String,
        risk: Risk,
        required: List<String> = emptyList(),
    ): Tool =
        object : Tool {
            override val spec =
                ToolSpec(
                    name,
                    "does $name",
                    buildJsonObject { if (required.isNotEmpty()) put("required", JsonArray(required.map(::JsonPrimitive))) },
                    risk,
                )

            override fun describe(args: JsonObject): CallDescription = CallDescription(name, "$name #${args.number()}")

            override suspend fun execute(
                args: JsonObject,
                context: ToolContext,
            ): ToolResult {
                executed += "$name #${args.number()}"
                return ToolResult.Success("$name done")
            }
        }

    private fun JsonObject.number(): String = this["n"]?.jsonPrimitive?.content.orEmpty()
}
