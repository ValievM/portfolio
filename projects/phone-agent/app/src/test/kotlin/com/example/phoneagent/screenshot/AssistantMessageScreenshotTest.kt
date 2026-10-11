package com.example.phoneagent.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.ui.designsystem.components.AssistantMessage
import com.example.phoneagent.ui.designsystem.components.LinkConfirmationContent
import com.example.phoneagent.ui.designsystem.components.LinkDecision
import com.example.phoneagent.ui.designsystem.components.evaluateLink
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PHONE_QUALIFIERS)
class AssistantMessageScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun reply(
        name: String,
        text: String,
        variants: List<Variant> = listOf(Variant.Light, Variant.Dark),
    ) = composeRule.snapshot("assistant_$name", variants, immediateWork = true) {
        Column(modifier = Modifier.padding(AppTheme.spacing.medium)) {
            AssistantMessage(text = text)
        }
    }

    @Test
    fun richMarkdown() = reply("markdown", MARKDOWN_REPLY, listOf(Variant.Light, Variant.Dark, Variant.LargeFont))

    @Test
    fun unclosedCodeFence() = reply("unclosed_fence", UNCLOSED_FENCE_REPLY)

    @Test
    fun longWords() = reply("long_words", LONG_CYRILLIC, listOf(Variant.Light, Variant.LargeFont))

    @Test
    fun imageAndHtmlStayInert() = reply("hostile", HOSTILE_REPLY)

    @Test
    fun linkConfirmation() =
        composeRule.snapshot("link_confirmation", listOf(Variant.Light, Variant.Dark, Variant.LargeFont)) {
            LinkConfirmationContent(
                decision = evaluateLink("https://accounts.google.com@evil.example/login?next=/settings"),
                onOpen = {},
                onDismiss = {},
            )
        }

    @Test
    fun linkBlocked() =
        composeRule.snapshot("link_blocked") {
            LinkConfirmationContent(decision = LinkDecision.Blocked, onOpen = {}, onDismiss = {})
        }
}
