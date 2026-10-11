package com.example.phoneagent.tools.notify

import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import com.example.phoneagent.domain.fakes.FakeUserInteraction
import com.example.phoneagent.domain.interaction.Capability
import com.example.phoneagent.domain.interaction.CapabilityResult
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.ToolContext
import com.example.phoneagent.domain.tools.ToolResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

public class NotifyToolTest {
    @Test
    public fun `does not notify when notification permission is denied`() =
        runTest {
            val interaction = FakeUserInteraction().apply { capabilityResult = CapabilityResult.Denied }
            val notifier = RecordingNotifier()
            val tool = NotifyTool(interaction, notifier)

            val result = tool.execute(args(title = "Готово", message = "Файл сохранён"), toolContext())

            assertEquals(ToolResult.Failed(NotifyCopy.PERMISSION_DENIED), result)
            assertEquals(listOf(Capability.NOTIFICATIONS), interaction.capabilityRequests)
            assertEquals(emptyList<NotifyArgs>(), notifier.notifications)
        }

    @Test
    public fun `shows a notification after permission is available`() =
        runTest {
            val interaction = FakeUserInteraction()
            val notifier = RecordingNotifier()
            val tool = NotifyTool(interaction, notifier)
            val notifyArgs = NotifyArgs(title = "Готово", message = "Файл сохранён")

            val result = tool.execute(args(title = notifyArgs.title, message = notifyArgs.message), toolContext())

            assertEquals(ToolResult.Success(NotifyCopy.NOTIFICATION_SHOWN), result)
            assertEquals(listOf(Capability.NOTIFICATIONS), interaction.capabilityRequests)
            assertEquals(listOf(notifyArgs), notifier.notifications)
        }

    @Test
    public fun `does not request permission when arguments are invalid`() =
        runTest {
            val interaction = FakeUserInteraction()
            val notifier = RecordingNotifier()
            val tool = NotifyTool(interaction, notifier)

            val result = tool.execute(args(title = "", message = "Текст"), toolContext())

            assertEquals(ToolResult.Failed(NotifyCopy.validationError(NotifyValidationError.EMPTY_TITLE)), result)
            assertEquals(emptyList<Capability>(), interaction.capabilityRequests)
            assertEquals(emptyList<NotifyArgs>(), notifier.notifications)
        }

    @Test
    public fun `declares a safe notification tool`() {
        val tool = NotifyTool(FakeUserInteraction(), RecordingNotifier())

        assertEquals("notify", tool.spec.name)
        assertEquals(Risk.SAFE, tool.spec.risk)
    }

    private fun args(
        title: String,
        message: String,
    ) = buildJsonObject {
        put("title", JsonPrimitive(title))
        put("message", JsonPrimitive(message))
    }

    private fun toolContext(): ToolContext = ToolContext(clock = FakeClock(), logger = FakeAppLogger())
}

private class RecordingNotifier : Notifier {
    val notifications: MutableList<NotifyArgs> = mutableListOf()

    override fun notify(
        title: String,
        message: String,
    ) {
        notifications += NotifyArgs(title, message)
    }
}
