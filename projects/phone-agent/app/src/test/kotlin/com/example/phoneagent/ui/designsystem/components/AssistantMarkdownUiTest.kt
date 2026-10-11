package com.example.phoneagent.ui.designsystem.components

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val PARSE_TIMEOUT_MILLIS = 5_000L

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class AssistantMarkdownUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val openedLinks = mutableListOf<String>()

    private fun show(text: String) {
        composeRule.setContent {
            AppTheme(useDynamicColor = false) {
                Column { AssistantMarkdown(text = text, onLinkClick = { openedLinks += it }) }
            }
        }
    }

    private fun awaitText(text: String) {
        composeRule.waitUntil(PARSE_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `markdown syntax is rendered as formatted text without the raw markers`() {
        show("# Plan\n\nSet the **alarm** first")

        awaitText("Plan")

        composeRule.onNodeWithText("Plan").assertIsDisplayed()
        composeRule.onNodeWithText("# Plan", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("**alarm**", substring = true).assertDoesNotExist()
    }

    @Test
    fun `an unclosed code fence renders the code received so far`() {
        show("Run this:\n\n```kotlin\nval hour = 7\nval minute =")

        awaitText("val hour = 7")

        composeRule.onNodeWithText("val minute =", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Run this:").assertIsDisplayed()
    }

    @Test
    fun `tapping a link reports it and never starts an activity on its own`() {
        show("[the docs](https://example.com/docs)")

        awaitText("the docs")
        composeRule.onNodeWithText("the docs").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("https://example.com/docs"), openedLinks)
        assertNull(shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity)
    }

    @Test
    fun `an image is never loaded and its address stays inert plain text instead of a link`() {
        show("![tracking pixel](https://tracker.example/p.png)")

        awaitText("tracker.example")
        composeRule.onNodeWithText("tracker.example", substring = true).performClick()
        composeRule.waitForIdle()

        // The renderer has no image loader here, so the address is just text: tapping it opens nothing.
        assertEquals(emptyList<String>(), openedLinks)
        assertNull(shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity)
    }

    @Test
    fun `growing text keeps the earlier part on screen while the rest is parsed`() {
        var text by mutableStateOf("First paragraph")
        composeRule.setContent {
            AppTheme(useDynamicColor = false) {
                Column { AssistantMarkdown(text = text, onLinkClick = {}) }
            }
        }
        awaitText("First paragraph")

        text = "First paragraph\n\nSecond paragraph"
        awaitText("Second paragraph")

        composeRule.onNodeWithText("First paragraph").assertIsDisplayed()
        composeRule.onNodeWithText("Second paragraph").assertIsDisplayed()
    }

    @Test
    fun `assistant message asks for confirmation showing the real domain before opening a link`() {
        val opened = mutableListOf<String>()
        composeRule.setContent {
            AppTheme(useDynamicColor = false) {
                AssistantMessage(text = "[safe looking](https://accounts.google.com@evil.example/login)", onOpenLink = { opened += it })
            }
        }
        awaitText("safe looking")

        composeRule.onNodeWithText("safe looking").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("evil.example").assertIsDisplayed()
        assertEquals(emptyList<String>(), opened)

        composeRule.onNodeWithText(string(R.string.link_open_action)).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("https://accounts.google.com@evil.example/login"), opened)
    }

    @Test
    fun `cancelling the confirmation opens nothing`() {
        val opened = mutableListOf<String>()
        composeRule.setContent {
            AppTheme(useDynamicColor = false) {
                AssistantMessage(text = "[docs](https://example.com/docs)", onOpenLink = { opened += it })
            }
        }
        awaitText("docs")
        composeRule.onNodeWithText("docs").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(string(R.string.link_cancel_action)).performClick()
        composeRule.waitForIdle()

        assertEquals(emptyList<String>(), opened)
        composeRule.onNodeWithText(string(R.string.link_open_confirm_title)).assertDoesNotExist()
    }

    @Test
    fun `a link that is not https is explained as blocked and cannot be opened`() {
        val opened = mutableListOf<String>()
        composeRule.setContent {
            AppTheme(useDynamicColor = false) {
                AssistantMessage(text = "[click me](javascript:alert(1))", onOpenLink = { opened += it })
            }
        }
        awaitText("click me")

        composeRule.onNodeWithText("click me").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(string(R.string.link_blocked_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.link_open_action)).assertDoesNotExist()
        assertEquals(emptyList<String>(), opened)
    }

    @Test
    fun `a raw html link from the answer stays inert text and never reports a uri`() {
        show("""Вот файл: <a href="https://evil.example/download?token=secret">скачать отчёт</a> <script>alert(1)</script>""")

        // The anchor text must be on screen at all: the renderer keeps the label visible whether
        // it treats the markup as a link or as plain text, so a missing node is a broken render,
        // not a pass. Without this assertion the test could pass vacuously on a bad render.
        awaitText("скачать отчёт")
        composeRule.onNodeWithText("скачать отчёт", substring = true).assertExists()

        // Whatever the anchor renders as, tapping it must not open anything and must not report
        // a URI for confirmation either.
        composeRule.onNodeWithText("скачать отчёт", substring = true).performClick()
        composeRule.waitForIdle()

        assertEquals(emptyList<String>(), openedLinks)
        composeRule.onNodeWithText(string(R.string.link_open_confirm_title)).assertDoesNotExist()
    }

    private fun string(id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)
}
