package com.example.phoneagent.ui.chat

import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.agent.AgentLoop
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
import com.example.phoneagent.domain.model.ModelEvent
import com.example.phoneagent.domain.model.ModelGateway
import com.example.phoneagent.domain.model.ModelRequest
import com.example.phoneagent.domain.storage.ConversationRepository
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.Tool
import com.example.phoneagent.domain.tools.ToolContent
import com.example.phoneagent.domain.tools.ToolContext
import com.example.phoneagent.domain.tools.ToolFile
import com.example.phoneagent.domain.tools.ToolPipeline
import com.example.phoneagent.domain.tools.ToolRegistry
import com.example.phoneagent.domain.tools.ToolResult
import com.example.phoneagent.domain.tools.ToolSpec
import com.example.phoneagent.ui.designsystem.components.FileHit
import com.example.phoneagent.ui.designsystem.components.ToolCallStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val interaction = FakeUserInteraction()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sending a message shows running state and appends fake response`() =
        runTest(dispatcher.scheduler) {
            val gateway =
                FakeModelGateway().apply {
                    events =
                        listOf(
                            ModelEvent.TextDelta("Готово"),
                            ModelEvent.Completed(),
                        )
                }
            val viewModel = createChatViewModel(agentLoop(gateway))
            val states = mutableListOf<ChatUiState>()
            backgroundScope.launch { viewModel.state.collect { states += it } }

            viewModel.onEvent(ChatUiEvent.Submit("Привет"))
            advanceUntilIdle()

            assertTrue(states.any { it.turn.isInProgress() })
            assertEquals(
                listOf(ChatRole.USER, ChatRole.ASSISTANT),
                viewModel.state.value.messages
                    .map(ChatMessage::role),
            )
            assertEquals(
                "Готово",
                viewModel.state.value.messages
                    .last()
                    .text,
            )
            assertFalse(
                viewModel.state.value.turn
                    .isInProgress(),
            )
        }

    @Test
    fun `view model restores conversation after restarting`() =
        runTest(dispatcher.scheduler) {
            val repository = FakeConversationRepository(emptyList())
            val firstRun =
                ChatViewModel(
                    agentLoop(
                        FakeModelGateway().apply {
                            events = listOf(ModelEvent.TextDelta("Earlier answer"), ModelEvent.Completed())
                        },
                    ),
                    repository,
                )
            firstRun.onEvent(ChatUiEvent.Submit("Earlier question"))
            advanceUntilIdle()

            val restarted = ChatViewModel(agentLoop(FakeModelGateway()), repository)
            advanceUntilIdle()
            assertEquals(
                listOf(ChatRole.USER to "Earlier question", ChatRole.ASSISTANT to "Earlier answer"),
                restarted.state.value.messages
                    .map { it.role to it.text },
            )
        }

    @Test
    fun `external history replacement clears persisted and visible conversation`() =
        runTest(dispatcher.scheduler) {
            val repository =
                FakeConversationRepository(
                    listOf(
                        HistoryItem.UserMessage(ItemId("saved-user"), "Earlier question"),
                        HistoryItem.AssistantMessage(ItemId("saved-assistant"), "Earlier answer"),
                    ),
                )
            val viewModel = ChatViewModel(agentLoop(FakeModelGateway()), repository)
            advanceUntilIdle()

            repository.replace(emptyList())
            advanceUntilIdle()

            assertEquals(emptyList<HistoryItem>(), repository.load())
            assertEquals(emptyList<ChatMessage>(), viewModel.state.value.messages)
        }

    @Test
    fun `external empty history emission cancels active turn and clears chat`() =
        runTest(dispatcher.scheduler) {
            val initialHistory =
                listOf(
                    HistoryItem.UserMessage(ItemId("saved-user"), "Earlier question"),
                    HistoryItem.AssistantMessage(ItemId("saved-assistant"), "Earlier answer"),
                )
            val repository = FakeConversationRepository(initialHistory)
            val gateway = HangingModelGateway()
            val viewModel = ChatViewModel(agentLoop(gateway), repository)
            advanceUntilIdle()

            viewModel.onEvent(ChatUiEvent.Submit("New question"))
            runCurrent()
            assertTrue(
                viewModel.state.value.turn
                    .isInProgress(),
            )

            try {
                repository.replace(emptyList())
                runCurrent()

                assertTrue(gateway.wasCancelled)
                assertFalse(
                    viewModel.state.value.turn
                        .isInProgress(),
                )
                assertEquals(emptyList<ChatMessage>(), viewModel.state.value.messages)
                assertEquals(emptyList<HistoryItem>(), repository.load())
            } finally {
                viewModel.onEvent(ChatUiEvent.Stop)
                runCurrent()
            }
        }

    @Test
    fun `initial history emission does not cancel submit waiting for startup load`() =
        runTest(dispatcher.scheduler) {
            val initialHistory = listOf(HistoryItem.UserMessage(ItemId("saved-user"), "Earlier question"))
            val allowInitialEmission = CompletableDeferred<Unit>()
            val updates = MutableSharedFlow<List<HistoryItem>>(extraBufferCapacity = 1)
            val repository =
                object : ConversationRepository {
                    private var items: List<HistoryItem> = initialHistory

                    override suspend fun load(): List<HistoryItem> = items

                    override fun observe(): Flow<List<HistoryItem>> =
                        flow {
                            allowInitialEmission.await()
                            emit(initialHistory)
                            emitAll(updates)
                        }

                    override suspend fun replace(items: List<HistoryItem>) {
                        this.items = items
                        updates.emit(items)
                    }
                }
            val gateway = FakeModelGateway().apply { events = listOf(ModelEvent.TextDelta("Answer"), ModelEvent.Completed()) }
            val viewModel = ChatViewModel(agentLoop(gateway), repository)
            runCurrent()

            viewModel.onEvent(ChatUiEvent.Submit("New question"))
            runCurrent()
            assertTrue(
                viewModel.state.value.turn
                    .isInProgress(),
            )
            assertTrue(gateway.requests.isEmpty())

            allowInitialEmission.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, gateway.requests.size)
            assertFalse(
                viewModel.state.value.turn
                    .isInProgress(),
            )
            assertEquals(
                listOf("Earlier question", "New question", "Answer"),
                viewModel.state.value.messages
                    .map(ChatMessage::text),
            )
        }

    @Test
    fun `completed cancelled turn cannot restore history after external clear`() =
        runTest(dispatcher.scheduler) {
            val repository =
                FakeConversationRepository(
                    listOf(HistoryItem.UserMessage(ItemId("saved-user"), "Earlier question")),
                )
            val finishTurn = CompletableDeferred<Unit>()
            var wasCancelled = false
            val gateway =
                object : ModelGateway {
                    override fun stream(request: ModelRequest): Flow<ModelEvent> =
                        flow {
                            emit(ModelEvent.TextDelta("partial"))
                            try {
                                finishTurn.await()
                            } catch (exception: CancellationException) {
                                wasCancelled = true
                                throw exception
                            }
                            emit(ModelEvent.Completed())
                        }
                }
            val viewModel = ChatViewModel(agentLoop(gateway), repository)
            advanceUntilIdle()
            viewModel.onEvent(ChatUiEvent.Submit("New question"))
            runCurrent()

            try {
                repository.replace(emptyList())
                runCurrent()
                finishTurn.complete(Unit)
                advanceUntilIdle()

                assertTrue(wasCancelled)
                // The question is saved when it is sent; what must never happen is a write after the clear.
                assertEquals(emptyList<HistoryItem>(), repository.replacements.last())
                assertEquals(emptyList<HistoryItem>(), repository.load())
            } finally {
                viewModel.onEvent(ChatUiEvent.Stop)
                runCurrent()
            }
        }

    @Test
    fun `failed cancelled turn cannot restore history after external clear`() =
        runTest(dispatcher.scheduler) {
            val repository =
                FakeConversationRepository(
                    listOf(HistoryItem.UserMessage(ItemId("saved-user"), "Earlier question")),
                )
            val finishTurn = CompletableDeferred<Unit>()
            var wasCancelled = false
            val gateway =
                object : ModelGateway {
                    override fun stream(request: ModelRequest): Flow<ModelEvent> =
                        flow {
                            emit(ModelEvent.TextDelta("partial"))
                            try {
                                finishTurn.await()
                            } catch (exception: CancellationException) {
                                wasCancelled = true
                                throw exception
                            }
                            emit(ModelEvent.Failed(AgentError.Offline))
                        }
                }
            val viewModel = ChatViewModel(agentLoop(gateway), repository)
            advanceUntilIdle()
            viewModel.onEvent(ChatUiEvent.Submit("New question"))
            runCurrent()

            try {
                repository.replace(emptyList())
                runCurrent()
                finishTurn.complete(Unit)
                advanceUntilIdle()

                assertTrue(wasCancelled)
                // The question is saved when it is sent; what must never happen is a write after the clear.
                assertEquals(emptyList<HistoryItem>(), repository.replacements.last())
                assertEquals(emptyList<HistoryItem>(), repository.load())
            } finally {
                viewModel.onEvent(ChatUiEvent.Stop)
                runCurrent()
            }
        }

    @Test
    fun `model failure is exposed in state without crashing`() =
        runTest(dispatcher.scheduler) {
            val expectedError = AgentError.Offline
            val gateway = FakeModelGateway().apply { events = listOf(ModelEvent.Failed(expectedError)) }
            val viewModel = createChatViewModel(agentLoop(gateway))

            viewModel.onEvent(ChatUiEvent.Submit("Проверка"))
            advanceUntilIdle()

            assertEquals(expectedError, viewModel.state.value.errorForDisplay())
            assertTrue(viewModel.state.value.turn is com.example.phoneagent.domain.conversation.TurnState.FailedRetryable)
            assertFalse(
                viewModel.state.value.turn
                    .isInProgress(),
            )
        }

    @Test
    fun `stopping mid-response cancels the stream, clears running state, and keeps partial text`() =
        runTest(dispatcher.scheduler) {
            val gateway = HangingModelGateway()
            val viewModel = createChatViewModel(agentLoop(gateway))

            viewModel.onEvent(ChatUiEvent.Submit("Привет"))
            advanceUntilIdle()
            assertTrue(
                viewModel.state.value.turn
                    .isInProgress(),
            )

            viewModel.onEvent(ChatUiEvent.Stop)
            advanceUntilIdle()

            assertTrue(gateway.wasCancelled)
            assertFalse(
                viewModel.state.value.turn
                    .isInProgress(),
            )
            assertEquals(
                "partial",
                viewModel.state.value.messages
                    .last()
                    .text,
            )
        }

    @Test
    fun `a SAFE tool call renders as a card that goes from running to done`() =
        runTest(dispatcher.scheduler) {
            val call = ToolCall(CallId("call-1"), "get_time", buildJsonObject {})
            val gateway =
                FakeModelGateway().apply {
                    events = listOf(ModelEvent.ToolCallRequested(call), ModelEvent.Completed())
                }
            val registry = ToolRegistry(setOf(fakeTool("get_time") { ToolResult.Success("noon") }))
            val viewModel = createChatViewModel(agentLoop(gateway, registry))

            viewModel.onEvent(ChatUiEvent.Submit("который час"))
            advanceUntilIdle()

            val toolMessage =
                viewModel.state.value.messages
                    .first { it.role == ChatRole.TOOL }
            assertEquals(ToolCallStatus.Done("noon"), toolMessage.toolCall?.status)
        }

    @Test
    fun `structured file content renders without knowing the tool name`() =
        runTest(dispatcher.scheduler) {
            val call = ToolCall(CallId("call-1"), "files_plugin", buildJsonObject {})
            val gateway =
                FakeModelGateway().apply {
                    events = listOf(ModelEvent.ToolCallRequested(call), ModelEvent.Completed())
                }
            val registry =
                ToolRegistry(
                    setOf(
                        fakeTool("files_plugin") {
                            ToolResult.Success(
                                "model-facing output",
                                ToolContent.FilesFound(
                                    listOf(ToolFile("report.pdf", "content://media/external/downloads/42", 1024L, "application/pdf")),
                                ),
                            )
                        },
                    ),
                )
            val viewModel = createChatViewModel(agentLoop(gateway, registry))

            viewModel.onEvent(ChatUiEvent.Submit("найди отчет"))
            advanceUntilIdle()

            val toolMessage =
                viewModel.state.value.messages
                    .first { it.role == ChatRole.TOOL }
            assertEquals(
                listOf(FileHit("report.pdf", "content://media/external/downloads/42", 1024L, "application/pdf")),
                toolMessage.toolCall?.fileHits,
            )
            assertEquals(ToolCallStatus.FilesFound(1), toolMessage.toolCall?.status)
        }

    @Test
    fun `a CONFIRM tool call shows awaiting approval before the user decides`() =
        runTest(dispatcher.scheduler) {
            interaction.approvalResult = false
            val call = ToolCall(CallId("call-1"), "set_alarm", buildJsonObject {})
            val gateway =
                FakeModelGateway().apply {
                    events = listOf(ModelEvent.ToolCallRequested(call), ModelEvent.Completed())
                }
            val registry = ToolRegistry(setOf(fakeTool("set_alarm", risk = Risk.CONFIRM) { ToolResult.Success("set") }))
            val viewModel = createChatViewModel(agentLoop(gateway, registry))

            viewModel.onEvent(ChatUiEvent.Submit("поставь будильник"))
            advanceUntilIdle()

            val toolMessage =
                viewModel.state.value.messages
                    .first { it.role == ChatRole.TOOL }
            assertEquals(ToolCallStatus.Rejected, toolMessage.toolCall?.status)
        }

    @Test
    fun `toggling a tool call flips its expanded state`() =
        runTest(dispatcher.scheduler) {
            val call = ToolCall(CallId("call-1"), "get_time", buildJsonObject {})
            val gateway =
                FakeModelGateway().apply {
                    events = listOf(ModelEvent.ToolCallRequested(call), ModelEvent.Completed())
                }
            val registry = ToolRegistry(setOf(fakeTool("get_time") { ToolResult.Success("noon") }))
            val viewModel = createChatViewModel(agentLoop(gateway, registry))
            viewModel.onEvent(ChatUiEvent.Submit("который час"))
            advanceUntilIdle()
            val id =
                viewModel.state.value.messages
                    .first { it.role == ChatRole.TOOL }
                    .id

            viewModel.onEvent(ChatUiEvent.ToggleToolCallDetails(id))

            assertTrue(
                viewModel.state.value.messages
                    .first { it.id == id }
                    .toolCall
                    ?.expanded == true,
            )
        }

    @Test
    fun `stopping while a tool call is pending marks its card cancelled`() =
        runTest(dispatcher.scheduler) {
            val call = ToolCall(CallId("call-1"), "get_time", buildJsonObject {})
            val gateway = HangingAfterToolCallGateway(call)
            val registry = ToolRegistry(setOf(fakeTool("get_time") { awaitCancellation() }))
            val viewModel = createChatViewModel(agentLoop(gateway, registry))

            viewModel.onEvent(ChatUiEvent.Submit("который час"))
            // Not advanceUntilIdle(): ToolPipeline wraps execute() in a 5s withTimeout, and
            // advanceUntilIdle() would fast-forward straight past it before Stop ever runs.
            runCurrent()

            viewModel.onEvent(ChatUiEvent.Stop)
            advanceUntilIdle()

            val toolMessage =
                viewModel.state.value.messages
                    .first { it.role == ChatRole.TOOL }
            assertEquals(ToolCallStatus.Cancelled, toolMessage.toolCall?.status)
        }

    private fun agentLoop(
        gateway: ModelGateway,
        registry: ToolRegistry = ToolRegistry(emptySet()),
    ): AgentLoop =
        AgentLoop(
            modelGateway = gateway,
            toolPipeline =
                ToolPipeline(
                    registry = registry,
                    userInteraction = interaction,
                    clock = FakeClock(),
                    logger = FakeAppLogger(),
                ),
            contextWindowPolicy = ContextWindowPolicy { it },
            idGenerator = FakeIdGenerator(),
        )

    private fun createChatViewModel(agentLoop: AgentLoop): ChatViewModel = ChatViewModel(agentLoop, FakeConversationRepository(emptyList()))

    private class FakeConversationRepository(
        initialItems: List<HistoryItem>,
    ) : ConversationRepository {
        private var items = initialItems
        private val mutableItems = MutableStateFlow(initialItems)
        val replacements = mutableListOf<List<HistoryItem>>()

        override suspend fun load(): List<HistoryItem> = items

        override fun observe(): Flow<List<HistoryItem>> = mutableItems

        override suspend fun replace(items: List<HistoryItem>) {
            replacements += items
            this.items = items
            mutableItems.value = items
        }
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
            ): ToolResult = execute()
        }

    private class HangingModelGateway : ModelGateway {
        var wasCancelled: Boolean = false

        override fun stream(request: ModelRequest): Flow<ModelEvent> =
            flow {
                emit(ModelEvent.TextDelta("partial"))
                try {
                    awaitCancellation()
                } finally {
                    wasCancelled = true
                }
            }
    }

    /** First call requests a tool (that itself never returns); a second call would hang forever if made. */
    private class HangingAfterToolCallGateway(
        private val call: ToolCall,
    ) : ModelGateway {
        override fun stream(request: ModelRequest): Flow<ModelEvent> =
            flow {
                emit(ModelEvent.ToolCallRequested(call))
                emit(ModelEvent.Completed())
            }
    }
}
