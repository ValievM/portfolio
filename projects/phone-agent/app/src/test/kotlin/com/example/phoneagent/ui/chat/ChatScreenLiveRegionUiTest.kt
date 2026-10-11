package com.example.phoneagent.ui.chat

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.domain.conversation.ItemId
import com.example.phoneagent.domain.conversation.TurnState
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class ChatScreenLiveRegionUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the running status is a polite live region so TalkBack announces it once`() {
        composeRule.setContent {
            AppTheme(useDynamicColor = false) {
                ChatScreen(state = ChatUiState(turn = TurnState.Running(ItemId("question-1"))), onEvent = {}, onOpenSettings = {})
            }
        }

        val liveRegion =
            composeRule
                .onNodeWithText("Готовлю ответ")
                .fetchSemanticsNode()
                .config
                .getOrElseNullable(SemanticsProperties.LiveRegion) { null }

        assertTrue("running status must be a polite live region, was: $liveRegion", liveRegion == LiveRegionMode.Polite)
    }
}
