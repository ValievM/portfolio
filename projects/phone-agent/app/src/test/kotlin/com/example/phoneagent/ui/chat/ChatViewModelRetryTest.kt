package com.example.phoneagent.ui.chat

import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.agent.AgentLoop
import com.example.phoneagent.domain.conversation.ContextWindowPolicy
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.conversation.TurnState
import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import com.example.phoneagent.domain.fakes.FakeIdGenerator
import com.example.phoneagent.domain.fakes.FakeModelGateway
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelRetryTest {
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
    fun `a retryable failure offers a retry and a non-retryable one does not`() =
        runTest(dispatcher.scheduler) {
            val offline = viewModelWith(listOf(ModelEvent.Failed(AgentError.Offline)))
            val forbidden = viewModelWith(listOf(ModelEvent.Failed(AgentError.Forbidden)))

            offline.first.onEvent(ChatUiEvent.Submit("Привет"))
            forbidden.first.onEvent(ChatUiEvent.Submit("Привет"))
            advanceUntilIdle()

            assertTrue(offline.first.state.value.turn is TurnState.FailedRetryable)
            assertTrue(
                offline.first.state.value.turn
                    .hasRetryAction(),
            )
            assertTrue(forbidden.first.state.value.turn is TurnState.FailedTerminal)
            assertFalse(
                forbidden.first.state.value.turn
                    .hasRetryAction(),
            )
        }

    @Test
    fun `retry sends the same text once and replaces the failed exchange`() =
        runTest(dispatcher.scheduler) {
            val (viewModel, gateway, repository) =
                viewModelWith(
                    listOf(ModelEvent.TextDelta("half an ans"), ModelEvent.Failed(AgentError.Timeout)),
                    listOf(ModelEvent.TextDelta("Full answer"), ModelEvent.Completed()),
                )
            viewModel.onEvent(ChatUiEvent.Submit("Привет"))
            advanceUntilIdle()

            viewModel.onEvent(ChatUiEvent.Retry)
            advanceUntilIdle()

            assertEquals(2, gateway.requests.size)
            assertEquals(
                listOf("Привет"),
                gateway.requests[1]
                    .history
                    .filterIsInstance<HistoryItem.UserMessage>()
                    .map(HistoryItem.UserMessage::text),
            )
            assertEquals(
                listOf(ChatRole.USER to "Привет", ChatRole.ASSISTANT to "Full answer"),
                viewModel.state.value.messages
                    .map { it.role to it.text },
            )
            assertNull(viewModel.state.value.errorForDisplay())
            assertEquals(TurnState.Idle, viewModel.state.value.turn)
            assertFalse(
                viewModel.state.value.turn
                    .hasRetryAction(),
            )
            assertEquals(
                listOf("Привет", "Full answer"),
                repository.load().map { (it as? HistoryItem.UserMessage)?.text ?: (it as HistoryItem.AssistantMessage).text },
            )
        }

    @Test
    fun `retry redoes only the failed turn and keeps the earlier conversation`() =
        runTest(dispatcher.scheduler) {
            val (viewModel, gateway, _) =
                viewModelWith(
                    listOf(ModelEvent.TextDelta("First answer"), ModelEvent.Completed()),
                    listOf(ModelEvent.Failed(AgentError.Offline)),
                    listOf(ModelEvent.TextDelta("Second answer"), ModelEvent.Completed()),
                )
            viewModel.onEvent(ChatUiEvent.Submit("One"))
            advanceUntilIdle()
            viewModel.onEvent(ChatUiEvent.Submit("Two"))
            advanceUntilIdle()

            viewModel.onEvent(ChatUiEvent.Retry)
            advanceUntilIdle()

            assertEquals(
                listOf("One", "Two"),
                gateway.requests
                    .last()
                    .history
                    .filterIsInstance<HistoryItem.UserMessage>()
                    .map(HistoryItem.UserMessage::text),
            )
            assertEquals(
                listOf("One", "First answer", "Two", "Second answer"),
                viewModel.state.value.messages
                    .map(ChatMessage::text),
            )
        }

    @Test
    fun `retry does nothing when the last turn did not fail`() =
        runTest(dispatcher.scheduler) {
            val (viewModel, gateway, _) = viewModelWith(listOf(ModelEvent.TextDelta("Ok"), ModelEvent.Completed()))
            viewModel.onEvent(ChatUiEvent.Submit("Привет"))
            advanceUntilIdle()

            viewModel.onEvent(ChatUiEvent.Retry)
            advanceUntilIdle()

            assertEquals(1, gateway.requests.size)
            assertEquals(2, viewModel.state.value.messages.size)
        }

    @Test
    fun `retry does nothing after the error was dismissed`() =
        runTest(dispatcher.scheduler) {
            val (viewModel, gateway, _) = viewModelWith(listOf(ModelEvent.Failed(AgentError.Offline)))
            viewModel.onEvent(ChatUiEvent.Submit("Привет"))
            advanceUntilIdle()
            viewModel.onEvent(ChatUiEvent.DismissError)

            viewModel.onEvent(ChatUiEvent.Retry)
            advanceUntilIdle()

            assertEquals(1, gateway.requests.size)
            assertFalse(
                viewModel.state.value.turn
                    .hasRetryAction(),
            )
        }

    @Test
    fun `a stop pressed by the user is not shown as an error`() =
        runTest(dispatcher.scheduler) {
            val (viewModel, _, _) = viewModelWith(listOf(ModelEvent.Failed(AgentError.Cancelled)))

            viewModel.onEvent(ChatUiEvent.Submit("Привет"))
            advanceUntilIdle()

            assertNull(
                viewModel.state.value
                    .errorForDisplay()
                    ?.toPresentation(),
            )
            assertFalse(
                viewModel.state.value.turn
                    .hasRetryAction(),
            )
        }

    @Test
    fun `a retry saves only the question again so the failed attempt cannot come back`() =
        runTest(dispatcher.scheduler) {
            val repository = RetryFakeRepository()
            val gateway = FailThenHangGateway()
            val viewModel = ChatViewModel(agentLoop(gateway), repository)
            viewModel.onEvent(ChatUiEvent.Submit("Привет"))
            advanceUntilIdle()
            assertEquals(1, repository.load().size)

            viewModel.onEvent(ChatUiEvent.Retry)
            runCurrent()

            try {
                assertTrue(
                    viewModel.state.value.turn
                        .isInProgress(),
                )
                assertEquals(
                    listOf("Привет"),
                    repository.load().map { (it as HistoryItem.UserMessage).text },
                )
            } finally {
                viewModel.onEvent(ChatUiEvent.Stop)
                runCurrent()
            }
        }

    private fun viewModelWith(vararg responses: List<ModelEvent>): Triple<ChatViewModel, FakeModelGateway, RetryFakeRepository> {
        val gateway = FakeModelGateway().apply { responses.forEach(responseQueue::addLast) }
        val repository = RetryFakeRepository()
        return Triple(ChatViewModel(agentLoop(gateway), repository), gateway, repository)
    }

    private fun agentLoop(gateway: ModelGateway): AgentLoop =
        AgentLoop(
            modelGateway = gateway,
            toolPipeline =
                ToolPipeline(
                    registry = ToolRegistry(emptySet()),
                    userInteraction = FakeUserInteraction(),
                    clock = FakeClock(),
                    logger = FakeAppLogger(),
                ),
            contextWindowPolicy = ContextWindowPolicy { it },
            idGenerator = FakeIdGenerator(),
        )

    private class RetryFakeRepository : ConversationRepository {
        private var items: List<HistoryItem> = emptyList()
        private val mutableItems = MutableStateFlow(items)

        override suspend fun load(): List<HistoryItem> = items

        override fun observe(): Flow<List<HistoryItem>> = mutableItems

        override suspend fun replace(items: List<HistoryItem>) {
            this.items = items
            mutableItems.value = items
        }
    }

    private class FailThenHangGateway : ModelGateway {
        private var calls = 0

        override fun stream(request: ModelRequest): Flow<ModelEvent> =
            flow {
                calls++
                if (calls == 1) {
                    emit(ModelEvent.Failed(AgentError.Offline))
                } else {
                    awaitCancellation()
                }
            }
    }
}
