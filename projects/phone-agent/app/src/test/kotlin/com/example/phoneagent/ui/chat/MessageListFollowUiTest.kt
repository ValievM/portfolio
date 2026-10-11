package com.example.phoneagent.ui.chat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val HISTORY_SIZE = 40
private const val FAR_FROM_BOTTOM_INDEX = 30
private const val AWAIT_DISPLAYED_MILLIS = 5_000L

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class MessageListFollowUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var messages by mutableStateOf(history())

    @Before
    fun showList() {
        composeRule.setContent {
            // Replies are parsed as markdown in the background, on a clock the test does not control: on a slow
            // machine the new text is not on screen yet when waitForIdle() returns. Inspection mode makes the
            // parse synchronous (the same switch the screenshot tests use), so the test stops depending on speed.
            CompositionLocalProvider(LocalInspectionMode provides true) {
                AppTheme(useDynamicColor = false) {
                    MessageList(
                        messages = messages,
                        onEvent = {},
                        onOpenFile = {},
                        onSuggestionClick = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    @Test
    fun `a new model message is followed while the reader is at the bottom`() {
        append(ChatRole.ASSISTANT, "New reply")

        composeRule.waitForIdle()

        awaitDisplayed("New reply")
        composeRule.onNodeWithTag(ChatTestTags.SCROLL_TO_BOTTOM_CHIP).assertDoesNotExist()
    }

    @Test
    fun `a new model message while reading older ones shows the chip and does not move the list`() {
        scrollFarFromBottom()

        append(ChatRole.ASSISTANT, "New reply")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ChatTestTags.SCROLL_TO_BOTTOM_CHIP).assertIsDisplayed()
        composeRule.onNodeWithText("New reply").assertDoesNotExist()
        awaitDisplayed("Message 10")
    }

    @Test
    fun `tapping the chip jumps to the newest message and hides the chip`() {
        scrollFarFromBottom()
        append(ChatRole.ASSISTANT, "New reply")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ChatTestTags.SCROLL_TO_BOTTOM_CHIP).performClick()
        composeRule.waitForIdle()

        awaitDisplayed("New reply")
        composeRule.onNodeWithTag(ChatTestTags.SCROLL_TO_BOTTOM_CHIP).assertDoesNotExist()
    }

    @Test
    fun `the users own message jumps to the bottom without a chip even from older messages`() {
        scrollFarFromBottom()

        append(ChatRole.USER, "My question")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("My question").assertIsDisplayed()
        composeRule.onNodeWithTag(ChatTestTags.SCROLL_TO_BOTTOM_CHIP).assertDoesNotExist()
    }

    @Test
    fun `scrolling back to the bottom by hand hides the chip`() {
        scrollFarFromBottom()
        append(ChatRole.ASSISTANT, "New reply")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ChatTestTags.MESSAGE_LIST).performScrollToIndex(0)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ChatTestTags.SCROLL_TO_BOTTOM_CHIP).assertDoesNotExist()
    }

    @Test
    fun `growing the newest message does not raise the chip while the reader is at the bottom`() {
        messages = messages.dropLast(1) + messages.last().copy(text = "Message 40 keeps streaming in")
        composeRule.waitForIdle()

        awaitDisplayed("Message 40 keeps streaming in")
        composeRule.onNodeWithTag(ChatTestTags.SCROLL_TO_BOTTOM_CHIP).assertDoesNotExist()
    }

    @Test
    fun `messages read top to bottom in chronological order which is the TalkBack traversal order`() {
        val tops =
            listOf("Message 38", "Message 39", "Message 40").map { text ->
                composeRule
                    .onNodeWithText(text)
                    .getBoundsInRoot()
                    .top.value
            }

        assertTrue("Expected ascending tops but was $tops", tops == tops.sorted() && tops.distinct().size == tops.size)
    }

    @Test
    fun `user and assistant messages are separate semantics nodes, one per bubble`() {
        // If bubbles ever merged into one node, TalkBack would read the whole chat as one blob.
        val userNodes = composeRule.onAllNodesWithText("Message 39").fetchSemanticsNodes()
        val assistantNodes = composeRule.onAllNodesWithText("Message 40").fetchSemanticsNodes()

        assertTrue(userNodes.size == 1)
        assertTrue(assistantNodes.size == 1)
    }

    @Test
    fun `the scroll-to-bottom chip is a clickable control announced by its description`() {
        scrollFarFromBottom()
        append(ChatRole.ASSISTANT, "New reply")
        composeRule.waitForIdle()

        val chip = composeRule.onNodeWithTag(ChatTestTags.SCROLL_TO_BOTTOM_CHIP)
        chip.assertHasClickAction()
        val description =
            chip
                .fetchSemanticsNode()
                .config
                .getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription) { null }
                ?.firstOrNull()
        assertTrue(
            "chip must have a content description for TalkBack, was: $description",
            description == "Прокрутить к новым сообщениям",
        )
    }

    private fun scrollFarFromBottom() {
        composeRule.onNodeWithTag(ChatTestTags.MESSAGE_LIST).performScrollToIndex(FAR_FROM_BOTTOM_INDEX)
        composeRule.waitForIdle()
    }

    /**
     * A model reply is laid out and parsed by machinery that runs outside the test clock, so on a slow machine
     * waitForIdle() can return before it is on screen. Wait (bounded) until it is displayed, then assert as usual.
     */
    private fun awaitDisplayed(text: String) {
        composeRule.waitUntil(timeoutMillis = AWAIT_DISPLAYED_MILLIS) {
            runCatching { composeRule.onNodeWithText(text).assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    private fun append(
        role: ChatRole,
        text: String,
    ) {
        messages = messages + ChatMessage("new-${messages.size}", role, text)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExist() = assertDoesNotExist()
}

private fun history(): List<ChatMessage> =
    (1..HISTORY_SIZE).map { index ->
        ChatMessage(
            id = "$index",
            role = if (index % 2 == 0) ChatRole.ASSISTANT else ChatRole.USER,
            text = "Message $index",
        )
    }
