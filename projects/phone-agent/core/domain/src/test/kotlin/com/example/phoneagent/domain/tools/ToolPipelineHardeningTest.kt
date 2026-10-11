package com.example.phoneagent.domain.tools

import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import com.example.phoneagent.domain.fakes.FakeUserInteraction
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.interaction.Capability
import com.example.phoneagent.domain.interaction.CapabilityResult
import com.example.phoneagent.domain.interaction.UserInteraction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPipelineHardeningTest {
    @Test
    fun `an exception thrown by a tool becomes a failed outcome without leaking its message`() =
        runTest {
            val tool = tool("boom") { throw IllegalStateException("secret /storage/emulated/0/private.pdf") }

            val outcome = pipeline(tool).run("boom", buildJsonObject {})

            assertEquals(ToolOutcome.Failed("tool failed: IllegalStateException"), outcome)
        }

    @Test
    fun `a cancellation raised by a tool still propagates`() =
        runTest {
            val tool = tool("stop") { throw CancellationException("stopped") }

            val result = runCatching { pipeline(tool).run("stop", buildJsonObject {}) }

            assertTrue(result.exceptionOrNull() is CancellationException)
        }

    @Test
    fun `a permission dialog the user takes long to answer does not count against the timeout`() =
        runTest {
            val interaction = SlowPermissions(firstAnswerMillis = 10_000)
            val tool = permissionTool(interaction)

            val outcome = pipeline(tool, interaction = interaction, timeoutMillis = 5_000).run("notify", buildJsonObject {})

            assertEquals(ToolOutcome.Success("shown"), outcome)
        }

    @Test
    fun `a denied permission skips the tool and reports the tool's own message after a single question`() =
        runTest {
            val interaction = FakeUserInteraction().apply { capabilityResult = CapabilityResult.Denied }
            var executed = false
            val tool =
                object : Tool by permissionTool(interaction) {
                    override fun deniedMessage(capability: Capability): String = "нет разрешения: $capability"

                    override suspend fun execute(
                        args: JsonObject,
                        context: ToolContext,
                    ): ToolResult {
                        executed = true
                        return ToolResult.Success("shown")
                    }
                }

            val outcome = pipeline(tool, interaction = interaction).run("notify", buildJsonObject {})

            assertEquals(ToolOutcome.Failed("нет разрешения: NOTIFICATIONS"), outcome)
            assertTrue(!executed)
            assertEquals(listOf(Capability.NOTIFICATIONS), interaction.capabilityRequests)
        }

    @Test
    fun `a tool that needs no permission never triggers a permission question`() =
        runTest {
            val interaction = FakeUserInteraction()

            pipeline(tool("plain") { ToolResult.Success("ok") }, interaction = interaction).run("plain", buildJsonObject {})

            assertTrue(interaction.capabilityRequests.isEmpty())
        }

    @Test
    fun `a broken result restorer is logged without exposing its exception message`() {
        val logger = FakeAppLogger()
        val baseTool = tool("restorer") { ToolResult.Success("unused") }
        val brokenTool =
            object : Tool by baseTool {
                override fun restoreContent(outputText: String): ToolContent? = error("private path /storage/private.txt")
            }
        val pipeline = ToolPipeline(ToolRegistry(setOf(brokenTool)), FakeUserInteraction(), FakeClock(), logger)

        val content = pipeline.restoreContent("restorer", "saved output")

        assertNull(content)
        assertEquals("tool content restore failed", logger.events.single().message)
        assertEquals("IllegalStateException", logger.events.single().fields["error"])
        assertTrue(logger.events.single().throwable == null)
        assertTrue(logger.events.none { event -> event.fields.values.any { it.contains("/storage/private.txt") } })
    }

    private fun pipeline(
        tool: Tool,
        interaction: UserInteraction = FakeUserInteraction(),
        timeoutMillis: Long = 1_000,
    ): ToolPipeline = ToolPipeline(ToolRegistry(setOf(tool)), interaction, FakeClock(), FakeAppLogger(), timeoutMillis = timeoutMillis)

    private fun tool(
        name: String,
        execute: suspend () -> ToolResult,
    ): Tool =
        object : Tool {
            override val spec = ToolSpec(name, "test tool", buildJsonObject {}, Risk.SAFE)

            override fun describe(args: JsonObject) = CallDescription(name, "summary")

            override suspend fun execute(
                args: JsonObject,
                context: ToolContext,
            ): ToolResult = execute()
        }

    /** Like the real notify tool: declares the permission AND asks again inside execute (instant once granted). */
    private fun permissionTool(interaction: UserInteraction): Tool =
        object : Tool {
            override val spec =
                ToolSpec("notify", "test tool", buildJsonObject {}, Risk.SAFE, requiredCapabilities = setOf(Capability.NOTIFICATIONS))

            override fun describe(args: JsonObject) = CallDescription("notify", "summary")

            override suspend fun execute(
                args: JsonObject,
                context: ToolContext,
            ): ToolResult {
                interaction.ensure(Capability.NOTIFICATIONS)
                return ToolResult.Success("shown")
            }
        }

    /** The first question waits for the user; later ones return at once, like Android after a grant. */
    private class SlowPermissions(
        private val firstAnswerMillis: Long,
    ) : UserInteraction {
        private val granted = mutableSetOf<Capability>()

        override suspend fun confirm(request: ApprovalRequest): Boolean = true

        override suspend fun confirmBatch(requests: List<ApprovalRequest>): Boolean = true

        override suspend fun ensure(capability: Capability): CapabilityResult {
            if (granted.add(capability)) delay(firstAnswerMillis)
            return CapabilityResult.Available
        }
    }
}
