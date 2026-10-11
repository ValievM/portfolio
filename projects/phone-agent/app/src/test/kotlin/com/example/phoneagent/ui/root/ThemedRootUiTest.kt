package com.example.phoneagent.ui.root

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.R
import com.example.phoneagent.domain.storage.AppSettings
import com.example.phoneagent.ui.designsystem.components.Composer
import com.example.phoneagent.ui.designsystem.components.ComposerMode
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class ThemedRootUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var retries = 0
    private var state by mutableStateOf<ThemeState>(ThemeState.Loading)

    private fun string(id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun show() {
        composeRule.setContent {
            AppTheme(useDynamicColor = false) {
                ThemedRoot(state = state, onRetry = { retries++ }) { Text("app content") }
            }
        }
    }

    @Test
    fun `while nothing is read the app content is not shown`() {
        show()

        composeRule.onNodeWithText("app content").assertDoesNotExist()
    }

    @Test
    fun `a failed first read shows the message with an explicit retry and no app content`() {
        state = ThemeState.Unavailable(lastKnown = null)
        show()

        composeRule.onNodeWithText(string(R.string.theme_load_failed)).assertIsDisplayed()
        composeRule.onNodeWithText("app content").assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.error_retry)).performClick()

        assertEquals(1, retries)
    }

    @Test
    fun `ready settings show the app and no notice`() {
        state = ThemeState.Ready(AppSettings())
        show()

        composeRule.onNodeWithText("app content").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.theme_settings_stale)).assertDoesNotExist()
    }

    @Test
    fun `a failure after ready keeps the app and offers a retry`() {
        state = ThemeState.Unavailable(lastKnown = AppSettings(useDarkTheme = true))
        show()

        composeRule.onNodeWithText("app content").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.theme_settings_stale)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.error_retry)).performClick()

        assertEquals(1, retries)
    }

    @Test
    fun `switching the theme keeps the draft and does not recreate the content`() {
        var created by mutableIntStateOf(0)
        var dark by mutableStateOf(false)
        composeRule.setContent {
            AppTheme(darkTheme = dark, useDynamicColor = false) {
                ThemedRoot(state = ThemeState.Ready(AppSettings()), onRetry = {}) {
                    DisposableEffect(Unit) {
                        created++
                        onDispose {}
                    }
                    var draft by rememberSaveable { mutableStateOf("") }
                    Column {
                        Composer(draft = draft, onDraftChange = { draft = it }, mode = ComposerMode.Ready, onSubmit = {}, onStop = {})
                    }
                }
            }
        }
        composeRule.onNodeWithText(string(R.string.message_hint)).performTextInput("не терять это")

        dark = true
        composeRule.waitForIdle()

        composeRule.onNodeWithText("не терять это").assertIsDisplayed()
        assertEquals("the content must be composed once, not again for a new theme", 1, created)
    }
}
