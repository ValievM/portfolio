package com.example.phoneagent.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.ui.designsystem.components.AssistantMessage
import com.example.phoneagent.ui.designsystem.components.UserMessage
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PHONE_QUALIFIERS)
class MessagesScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun conversation() =
        composeRule.snapshot("messages", listOf(Variant.Light, Variant.Dark, Variant.LargeFont)) {
            Column(modifier = Modifier.padding(AppTheme.spacing.medium)) {
                UserMessage("Поставь будильник на 7:30")
                AssistantMessage("Ставлю будильник на 07:30. Проверьте в приложении «Часы».")
            }
        }

    @Test
    fun longWords() =
        composeRule.snapshot("messages_long", listOf(Variant.Light, Variant.LargeFont)) {
            Column(modifier = Modifier.padding(AppTheme.spacing.medium)) {
                UserMessage(LONG_CYRILLIC)
                AssistantMessage(LONG_CYRILLIC)
            }
        }
}
