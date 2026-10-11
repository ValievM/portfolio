package com.example.phoneagent.screenshot

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.ui.designsystem.components.Composer
import com.example.phoneagent.ui.designsystem.components.ComposerMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PHONE_QUALIFIERS)
class ComposerScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun idle() =
        composeRule.snapshot("composer_idle", listOf(Variant.Light, Variant.Dark, Variant.LargeFont)) {
            Composer(draft = "", onDraftChange = {}, mode = ComposerMode.Ready, onSubmit = {}, onStop = {})
        }

    @Test
    fun typed() =
        composeRule.snapshot("composer_typed", listOf(Variant.Light, Variant.Dark, Variant.LargeFont)) {
            Composer(
                draft = "Поставь будильник на 7:30",
                onDraftChange = {},
                mode = ComposerMode.Ready,
                onSubmit = {},
                onStop = {},
            )
        }

    @Test
    fun running() =
        composeRule.snapshot("composer_running") {
            Composer(draft = "", onDraftChange = {}, mode = ComposerMode.Running, onSubmit = {}, onStop = {})
        }

    @Test
    fun unavailable() =
        composeRule.snapshot("composer_unavailable") {
            Composer(draft = "", onDraftChange = {}, mode = ComposerMode.Unavailable, onSubmit = {}, onStop = {})
        }
}

internal const val PHONE_QUALIFIERS = "w411dp-h891dp-port-xxhdpi"
