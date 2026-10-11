package com.example.phoneagent.domain.tools

import com.example.phoneagent.domain.conversation.CallId
import com.example.phoneagent.domain.conversation.ToolCall
import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import com.example.phoneagent.domain.fakes.FakeUserInteraction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPipelineTest {
    @Test
    fun `an unknown tool name fails without approving or executing`() =
        runTest {
            val interaction = FakeUserInteraction()
            val pipeline = pipeline(registry = ToolRegistry(emptySet()), interaction = interaction)

            val outcome = pipeline.run("set_alarm", buildJsonObject {})

            assertEquals(ToolOutcome.Failed("unknown tool: set_alarm"), outcome)
            assertTrue(interaction.approvalRequests.isEmpty())
        }

    @Test
    fun `a missing required argument fails without approving or executing`() =
        runTest {
            var executed = false
            val tool =
                fakeTool("set_alarm", required = listOf("hour")) {
                    executed = true
                    ToolResult.Success("ok")
                }
            val interaction = FakeUserInteraction()
            val pipeline = pipeline(registry = ToolRegistry(setOf(tool)), interaction = interaction)

            val outcome = pipeline.run("set_alarm", buildJsonObject {})

            assertEquals(ToolOutcome.Failed("missing required fields: hour"), outcome)
            assertTrue(interaction.approvalRequests.isEmpty())
            assertTrue(!executed)
        }

    @Test
    fun `a SAFE tool executes without asking for approval`() =
        runTest {
            val interaction = FakeUserInteraction()
            val tool = fakeTool("find_files", risk = Risk.SAFE) { ToolResult.Success("found nothing") }
            val pipeline = pipeline(registry = ToolRegistry(setOf(tool)), interaction = interaction)

            val outcome = pipeline.run("find_files", buildJsonObject {})

            assertEquals(ToolOutcome.Success("found nothing"), outcome)
            assertTrue(interaction.approvalRequests.isEmpty())
        }

    @Test
    fun `a CONFIRM tool asks for approval and is rejected without executing when declined`() =
        runTest {
            var executed = false
            val interaction = FakeUserInteraction().apply { approvalResult = false }
            val tool =
                fakeTool("set_alarm", risk = Risk.CONFIRM) {
                    executed = true
                    ToolResult.Success("ok")
                }
            val pipeline = pipeline(registry = ToolRegistry(setOf(tool)), interaction = interaction)

            val outcome = pipeline.run("set_alarm", buildJsonObject {})

            assertEquals(ToolOutcome.Rejected("user declined"), outcome)
            assertEquals(1, interaction.approvalRequests.size)
            assertTrue(!executed)
        }

    @Test
    fun `a CONFIRM tool executes after the user approves`() =
        runTest {
            val interaction = FakeUserInteraction().apply { approvalResult = true }
            val tool = fakeTool("set_alarm", risk = Risk.CONFIRM) { ToolResult.Success("done") }
            val pipeline = pipeline(registry = ToolRegistry(setOf(tool)), interaction = interaction)

            val outcome = pipeline.run("set_alarm", buildJsonObject {})

            assertEquals(ToolOutcome.Success("done"), outcome)
        }

    @Test
    fun `a tool that fails reports the reason`() =
        runTest {
            val tool = fakeTool("find_files") { ToolResult.Failed("folder not selected") }
            val pipeline = pipeline(registry = ToolRegistry(setOf(tool)))

            assertEquals(ToolOutcome.Failed("folder not selected"), pipeline.run("find_files", buildJsonObject {}))
        }

    @Test
    fun `output longer than the limit is truncated without splitting a multi-byte character`() =
        runTest {
            // "Привет" is all 2-byte Cyrillic UTF-8 characters; an odd byte limit forces the cut to
            // land mid-character, verifying the boundary gets pushed back rather than just sliced.
            val longText = "Привет".repeat(2000)
            val tool = fakeTool("find_files") { ToolResult.Success(longText) }
            val pipeline = pipeline(registry = ToolRegistry(setOf(tool)), maxOutputBytes = 4095)

            val outcome = pipeline.run("find_files", buildJsonObject {}) as ToolOutcome.Success

            val truncatedBytes = outcome.text.toByteArray(Charsets.UTF_8)
            assertTrue(truncatedBytes.size <= 4095)
            assertEquals(outcome.text, String(truncatedBytes, Charsets.UTF_8))
        }

    @Test
    fun `a tool that never returns is stopped after the timeout`() =
        runTest {
            val tool = fakeTool("find_files") { awaitCancellation() }
            val pipeline = pipeline(registry = ToolRegistry(setOf(tool)), timeoutMillis = 10)

            val outcome = pipeline.run("find_files", buildJsonObject {})

            assertEquals(ToolOutcome.Failed("timed out after 10ms"), outcome)
        }

    @Test
    fun `a SAFE tool with a broken describe shows a neutral card and still runs`() =
        runTest {
            var executed = false
            val tool =
                describingTool("find_files", Risk.SAFE, describeError = IllegalStateException("boom")) {
                    executed = true
                    ToolResult.Success("ok")
                }
            val pipeline = pipeline(registry = ToolRegistry(setOf(tool)))

            val description = pipeline.describe("find_files", buildJsonObject {})

            assertEquals("find_files", description?.title)
            assertTrue(description?.summary?.isBlank() == true)

            val outcome = pipeline.run("find_files", buildJsonObject {})

            assertEquals(ToolOutcome.Success("ok"), outcome)
            assertTrue(executed)
        }

    @Test
    fun `a CONFIRM tool with a broken describe refuses to run without asking`() =
        runTest {
            var executed = false
            val tool =
                describingTool("set_alarm", Risk.CONFIRM, describeError = IllegalStateException("boom")) {
                    executed = true
                    ToolResult.Success("ok")
                }
            val interaction = FakeUserInteraction()
            val pipeline = pipeline(registry = ToolRegistry(setOf(tool)), interaction = interaction)

            val outcome = pipeline.run("set_alarm", buildJsonObject {})

            assertTrue(outcome is ToolOutcome.Failed)
            assertTrue(interaction.approvalRequests.isEmpty())
            assertTrue(!executed)
        }

    @Test
    fun `a SENSITIVE tool with a broken describe also refuses to run without asking`() =
        runTest {
            var executed = false
            val tool =
                describingTool("send_sms", Risk.SENSITIVE, describeError = IllegalStateException("boom")) {
                    executed = true
                    ToolResult.Success("ok")
                }
            val interaction = FakeUserInteraction()
            val pipeline = pipeline(registry = ToolRegistry(setOf(tool)), interaction = interaction)

            val outcome = pipeline.run("send_sms", buildJsonObject {})

            assertTrue(outcome is ToolOutcome.Failed)
            assertTrue(interaction.approvalRequests.isEmpty())
            assertTrue(!executed)
        }

    @Test
    fun `a describe that works once and then breaks cannot break the turn because it is asked for only once`() =
        runTest {
            var describeCalls = 0
            val tool =
                object : Tool {
                    override val spec = ToolSpec("set_alarm", "test tool", buildJsonObject { put("type", "object") }, Risk.CONFIRM)

                    override fun describe(args: JsonObject): CallDescription {
                        describeCalls++
                        check(describeCalls == 1) { "describe was asked for again" }
                        return CallDescription("set_alarm", "summary")
                    }

                    override suspend fun execute(
                        args: JsonObject,
                        context: ToolContext,
                    ): ToolResult = ToolResult.Success("ok")
                }
            val interaction = FakeUserInteraction()

            val outcome = pipeline(registry = ToolRegistry(setOf(tool)), interaction = interaction).run("set_alarm", buildJsonObject {})

            assertEquals(ToolOutcome.Success("ok"), outcome)
            assertEquals(1, describeCalls)
            assertEquals(CallDescription("set_alarm", "summary"), interaction.approvalRequests.single().description)
        }

    @Test
    fun `a batch with a broken describe is declined without asking`() =
        runTest {
            val broken =
                describingTool("set_alarm", Risk.CONFIRM, describeError = IllegalStateException("boom")) { ToolResult.Success("ok") }
            val healthy = describingTool("send_notice", Risk.CONFIRM, describeError = null) { ToolResult.Success("ok") }
            val interaction = FakeUserInteraction()
            val pipeline = pipeline(registry = ToolRegistry(setOf(broken, healthy)), interaction = interaction, maxBatchSize = 4)
            val calls = listOf(call("set_alarm"), call("send_notice"))

            val approved = pipeline.confirmBatch(calls)

            assertTrue(!approved)
            assertTrue(interaction.batchRequests.isEmpty())
        }

    @Test
    fun `a batch containing a SENSITIVE call is declined without asking`() =
        runTest {
            val confirmTool = describingTool("set_alarm", Risk.CONFIRM, describeError = null) { ToolResult.Success("ok") }
            val sensitiveTool = describingTool("wipe_data", Risk.SENSITIVE, describeError = null) { ToolResult.Success("ok") }
            val interaction = FakeUserInteraction()
            val pipeline = pipeline(registry = ToolRegistry(setOf(confirmTool, sensitiveTool)), interaction = interaction, maxBatchSize = 4)
            val calls = listOf(call("set_alarm"), call("wipe_data"))

            val approved = pipeline.confirmBatch(calls)

            assertTrue(!approved)
            assertTrue(interaction.batchRequests.isEmpty())
        }

    @Test
    fun `a batch containing a SAFE call is declined without asking`() =
        runTest {
            val confirmTool = describingTool("set_alarm", Risk.CONFIRM, describeError = null) { ToolResult.Success("ok") }
            val safeTool = describingTool("find_files", Risk.SAFE, describeError = null) { ToolResult.Success("ok") }
            val interaction = FakeUserInteraction()
            val pipeline = pipeline(registry = ToolRegistry(setOf(confirmTool, safeTool)), interaction = interaction, maxBatchSize = 4)
            val calls = listOf(call("set_alarm"), call("find_files"))

            val approved = pipeline.confirmBatch(calls)

            assertTrue(!approved)
            assertTrue(interaction.batchRequests.isEmpty())
        }

    @Test
    fun `a batch containing an unknown tool is declined without asking and without throwing`() =
        runTest {
            val confirmTool = describingTool("set_alarm", Risk.CONFIRM, describeError = null) { ToolResult.Success("ok") }
            val interaction = FakeUserInteraction()
            val pipeline = pipeline(registry = ToolRegistry(setOf(confirmTool)), interaction = interaction, maxBatchSize = 4)
            val calls = listOf(call("set_alarm"), call("vanished_tool"))

            val approved = pipeline.confirmBatch(calls)

            assertTrue(!approved)
            assertTrue(interaction.batchRequests.isEmpty())
        }

    @Test
    fun `a CancellationException from describe is rethrown, never turned into a card`() {
        val tool = describingTool("find_files", Risk.SAFE, describeError = CancellationException("stop")) { ToolResult.Success("ok") }
        val pipeline = pipeline(registry = ToolRegistry(setOf(tool)))

        val outcome = runCatching { pipeline.describe("find_files", buildJsonObject {}) }

        assertTrue(outcome.exceptionOrNull() is CancellationException)
    }

    private fun call(name: String) = ToolCall(CallId(name), name, buildJsonObject { })

    private fun describingTool(
        name: String,
        risk: Risk,
        describeError: Throwable?,
        execute: suspend () -> ToolResult,
    ): Tool =
        object : Tool {
            override val spec =
                ToolSpec(
                    name = name,
                    description = "test tool",
                    inputSchema = buildJsonObject { put("type", "object") },
                    risk = risk,
                )

            override fun describe(args: JsonObject): CallDescription =
                describeError?.let(::throwIt) ?: CallDescription(title = name, summary = "summary")

            @Suppress("USELESS_ELVIS")
            private fun throwIt(error: Throwable): Nothing = throw error

            override suspend fun execute(
                args: JsonObject,
                context: ToolContext,
            ): ToolResult = execute()
        }

    private fun pipeline(
        registry: ToolRegistry,
        interaction: FakeUserInteraction = FakeUserInteraction(),
        timeoutMillis: Long = 1_000,
        maxOutputBytes: Int = 4096,
        maxBatchSize: Int = 3,
    ): ToolPipeline =
        ToolPipeline(
            registry = registry,
            userInteraction = interaction,
            clock = FakeClock(),
            logger = FakeAppLogger(),
            timeoutMillis = timeoutMillis,
            maxOutputBytes = maxOutputBytes,
            maxBatchSize = maxBatchSize,
        )

    private fun fakeTool(
        name: String,
        risk: Risk = Risk.SAFE,
        required: List<String> = emptyList(),
        execute: suspend () -> ToolResult,
    ): Tool =
        object : Tool {
            override val spec =
                ToolSpec(
                    name = name,
                    description = "test tool",
                    inputSchema =
                        buildJsonObject {
                            put("type", "object")
                            putJsonArray("required") { required.forEach { add(it) } }
                        },
                    risk = risk,
                )

            override fun describe(args: JsonObject): CallDescription = CallDescription(title = name, summary = "summary")

            override suspend fun execute(
                args: JsonObject,
                context: ToolContext,
            ): ToolResult = execute()
        }
}
