package com.example.phoneagent.ui.chat

import com.example.phoneagent.domain.agent.AgentLoop
import com.example.phoneagent.domain.conversation.CallId
import com.example.phoneagent.domain.conversation.ContextWindowPolicy
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.conversation.ToolCall
import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import com.example.phoneagent.domain.fakes.FakeIdGenerator
import com.example.phoneagent.domain.fakes.FakeModelGateway
import com.example.phoneagent.domain.fakes.FakeUserInteraction
import com.example.phoneagent.domain.model.ModelEvent
import com.example.phoneagent.domain.storage.ConversationRepository
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.Tool
import com.example.phoneagent.domain.tools.ToolContext
import com.example.phoneagent.domain.tools.ToolPipeline
import com.example.phoneagent.domain.tools.ToolRegistry
import com.example.phoneagent.domain.tools.ToolResult
import com.example.phoneagent.domain.tools.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelOrderTest {
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
    fun `a tool card stays between the texts around it after the turn is saved`() =
        runTest(dispatcher.scheduler) {
            val call = ToolCall(CallId("call-1"), "get_time", buildJsonObject {})
            val gateway =
                FakeModelGateway().apply {
                    responseQueue.addLast(
                        listOf(ModelEvent.TextDelta("Смотрю время"), ModelEvent.ToolCallRequested(call), ModelEvent.Completed()),
                    )
                    responseQueue.addLast(listOf(ModelEvent.TextDelta("Сейчас полдень"), ModelEvent.Completed()))
                }
            val registry = ToolRegistry(setOf(clockTool()))
            val viewModel = ChatViewModel(agentLoop(gateway, registry), InMemoryRepository())

            viewModel.onEvent(ChatUiEvent.Submit("который час"))
            advanceUntilIdle()

            assertEquals(
                listOf(ChatRole.USER, ChatRole.ASSISTANT, ChatRole.TOOL, ChatRole.ASSISTANT),
                viewModel.state.value.messages
                    .map(ChatMessage::role),
            )
            assertEquals(
                listOf("который час", "Смотрю время", "", "Сейчас полдень"),
                viewModel.state.value.messages
                    .map(ChatMessage::text),
            )
        }

    private fun clockTool(): Tool =
        object : Tool {
            override val spec = ToolSpec("get_time", "current time", buildJsonObject {}, Risk.SAFE)

            override fun describe(args: JsonObject): CallDescription = CallDescription("Время", "get_time")

            override suspend fun execute(
                args: JsonObject,
                context: ToolContext,
            ): ToolResult = ToolResult.Success("noon")
        }

    private fun agentLoop(
        gateway: FakeModelGateway,
        registry: ToolRegistry,
    ): AgentLoop =
        AgentLoop(
            modelGateway = gateway,
            toolPipeline = ToolPipeline(registry, FakeUserInteraction(), FakeClock(), FakeAppLogger()),
            contextWindowPolicy = ContextWindowPolicy { it },
            idGenerator = FakeIdGenerator(),
        )

    private class InMemoryRepository : ConversationRepository {
        private var items: List<HistoryItem> = emptyList()
        private val mutableItems = MutableStateFlow(items)

        override suspend fun load(): List<HistoryItem> = items

        override fun observe(): Flow<List<HistoryItem>> = mutableItems

        override suspend fun replace(items: List<HistoryItem>) {
            this.items = items
            mutableItems.value = items
        }
    }
}
