package com.example.phoneagent.ui.chat

import com.example.phoneagent.domain.agent.AgentLoop
import com.example.phoneagent.domain.conversation.ContextWindowPolicy
import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import com.example.phoneagent.domain.fakes.FakeIdGenerator
import com.example.phoneagent.domain.fakes.FakeUserInteraction
import com.example.phoneagent.domain.model.ModelEvent
import com.example.phoneagent.domain.model.ModelGateway
import com.example.phoneagent.domain.model.ModelRequest
import com.example.phoneagent.domain.storage.ConversationRepository
import com.example.phoneagent.domain.tools.ToolPipeline
import com.example.phoneagent.domain.tools.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Stop clears isRunning right away (so the composer unlocks), and the cancelled turn's finally
 * clears it a second time — later. If a new question is submitted in between, that late second
 * clear must not touch the new turn.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelStopRaceTest {
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
    fun `a submit right after stop keeps the new turn running and blocks further submits`() =
        runTest(dispatcher.scheduler) {
            val repository = Repository()
            val viewModel = ChatViewModel(agentLoop(HangingGateway()), repository)
            advanceUntilIdle()

            // Start the first turn for real (it hangs on the gateway), then Stop and re-submit
            // before the scheduler runs the cancelled turn's finally.
            viewModel.onEvent(ChatUiEvent.Submit("Первый"))
            runCurrent()
            viewModel.onEvent(ChatUiEvent.Stop)
            viewModel.onEvent(ChatUiEvent.Submit("Второй"))
            advanceUntilIdle()

            // The second turn is still waiting on the hanging gateway: the composer must stay locked.
            assertTrue(
                "the second turn must stay active, was ${viewModel.state.value.turn}",
                viewModel.state.value.turn
                    .isInProgress(),
            )

            // With the flag wrongly cleared, this third submit would start a parallel turn.
            val userMessagesBefore =
                viewModel.state.value.messages
                    .count { it.role == ChatRole.USER }
            viewModel.onEvent(ChatUiEvent.Submit("Третий"))
            advanceUntilIdle()
            assertEquals(
                userMessagesBefore,
                viewModel.state.value.messages
                    .count { it.role == ChatRole.USER },
            )
        }

    private fun agentLoop(gateway: ModelGateway): AgentLoop =
        AgentLoop(
            modelGateway = gateway,
            toolPipeline = ToolPipeline(ToolRegistry(emptySet()), FakeUserInteraction(), FakeClock(), FakeAppLogger()),
            contextWindowPolicy = ContextWindowPolicy { it },
            idGenerator = FakeIdGenerator(),
        )

    private class Repository : ConversationRepository {
        private var items: List<com.example.phoneagent.domain.conversation.HistoryItem> = emptyList()
        private val mutableItems = MutableStateFlow(items)

        override suspend fun load(): List<com.example.phoneagent.domain.conversation.HistoryItem> = items

        override fun observe(): Flow<List<com.example.phoneagent.domain.conversation.HistoryItem>> = mutableItems

        override suspend fun replace(items: List<com.example.phoneagent.domain.conversation.HistoryItem>) {
            this.items = items
            mutableItems.value = items
        }
    }

    private class HangingGateway : ModelGateway {
        override fun stream(request: ModelRequest): Flow<ModelEvent> =
            flow {
                emit(ModelEvent.TextDelta("частичный ответ"))
                awaitCancellation()
            }
    }
}
