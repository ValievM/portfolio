package com.example.phoneagent.ui.chat

import com.example.phoneagent.domain.agent.AgentLoop
import com.example.phoneagent.domain.conversation.CallId
import com.example.phoneagent.domain.conversation.ContextWindowPolicy
import com.example.phoneagent.domain.conversation.ToolCall
import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import com.example.phoneagent.domain.fakes.FakeConversationRepository
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
import com.example.phoneagent.domain.tools.ToolPipeline
import com.example.phoneagent.domain.tools.ToolRegistry
import com.example.phoneagent.domain.tools.ToolResult
import com.example.phoneagent.domain.tools.ToolSpec
import com.example.phoneagent.ui.designsystem.components.ToolCallStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelBatchApprovalTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `while the shared question is open every grouped card waits for approval, then they finish`() =
        runTest(dispatcher.scheduler) {
            val gate = CompletableDeferred<Boolean>()
            val viewModel = viewModelWith(GatedInteraction(gate))
            advanceUntilIdle()

            viewModel.onEvent(ChatUiEvent.Submit("три будильника"))
            runCurrent()

            assertEquals(List(3) { ToolCallStatus.AwaitingApproval }, toolStatuses(viewModel))

            gate.complete(true)
            advanceUntilIdle()

            assertTrue(toolStatuses(viewModel).all { it is ToolCallStatus.Done })
        }

    @Test
    fun `declining the shared question marks every grouped card rejected`() =
        runTest(dispatcher.scheduler) {
            val viewModel = viewModelWith(FakeUserInteraction().apply { batchApprovalResult = false })
            advanceUntilIdle()

            viewModel.onEvent(ChatUiEvent.Submit("три будильника"))
            advanceUntilIdle()

            assertEquals(List(3) { ToolCallStatus.Rejected }, toolStatuses(viewModel))
        }

    private fun toolStatuses(viewModel: ChatViewModel) =
        viewModel.state.value.messages
            .filter { it.role == ChatRole.TOOL }
            .map { it.toolCall?.status }

    private fun viewModelWith(interaction: UserInteraction): ChatViewModel {
        val calls = (1..3).map { ToolCall(CallId("call-$it"), "set_alarm", buildJsonObject {}) }
        val gateway =
            FakeModelGateway().apply {
                responseQueue.addLast(calls.map { ModelEvent.ToolCallRequested(it) } + ModelEvent.Completed())
                responseQueue.addLast(listOf(ModelEvent.TextDelta("готово"), ModelEvent.Completed()))
            }
        val alarm =
            object : Tool {
                override val spec = ToolSpec("set_alarm", "sets an alarm", buildJsonObject {}, Risk.CONFIRM)

                override fun describe(args: JsonObject) = CallDescription("set_alarm", "calling set_alarm")

                override suspend fun execute(
                    args: JsonObject,
                    context: ToolContext,
                ) = ToolResult.Success("set")
            }
        val loop =
            AgentLoop(
                modelGateway = gateway,
                toolPipeline = ToolPipeline(ToolRegistry(setOf(alarm)), interaction, FakeClock(), FakeAppLogger()),
                contextWindowPolicy = ContextWindowPolicy { it },
                idGenerator = FakeIdGenerator(),
            )
        return ChatViewModel(loop, FakeConversationRepository())
    }

    private class GatedInteraction(
        private val gate: CompletableDeferred<Boolean>,
    ) : UserInteraction {
        override suspend fun confirm(request: ApprovalRequest): Boolean = gate.await()

        override suspend fun confirmBatch(requests: List<ApprovalRequest>): Boolean = gate.await()

        override suspend fun ensure(capability: Capability): CapabilityResult = CapabilityResult.Available
    }
}
