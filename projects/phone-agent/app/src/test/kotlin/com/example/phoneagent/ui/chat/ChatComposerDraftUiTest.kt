package com.example.phoneagent.ui.chat

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class ChatComposerDraftUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `composer draft survives an activity recreation`() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            AppTheme(useDynamicColor = false) {
                ChatScreen(state = ChatUiState(), onEvent = {}, onOpenSettings = {})
            }
        }
        val hint =
            ApplicationProvider
                .getApplicationContext<Context>()
                .getString(R.string.message_hint)
        val draft = "Найди отчёт за сентябрь"

        composeRule.onNodeWithText(hint).performTextInput(draft)
        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText(draft).assertExists()
    }
}
