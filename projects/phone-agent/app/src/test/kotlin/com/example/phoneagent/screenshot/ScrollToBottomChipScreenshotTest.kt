package com.example.phoneagent.screenshot

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.ui.designsystem.components.ScrollToBottomChip
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PHONE_QUALIFIERS)
class ScrollToBottomChipScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chip() =
        composeRule.snapshot("scroll_to_bottom_chip", listOf(Variant.Light, Variant.Dark, Variant.LargeFont)) {
            ScrollToBottomChip(onClick = {})
        }
}
