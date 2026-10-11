package com.example.phoneagent.ui.designsystem.components

import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class PreparingIndicatorUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setAnimatorScale(scale: Float) {
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    @Test
    fun `the indicator says what the assistant is doing`() {
        composeRule.setContent { AppTheme(useDynamicColor = false) { PreparingIndicator() } }

        composeRule.onNodeWithText("Готовлю ответ").assertIsDisplayed()
    }

    @Test
    fun `reduced motion is off by default`() {
        var reduced by mutableStateOf(true)
        composeRule.setContent { reduced = rememberReducedMotion() }

        composeRule.waitForIdle()

        assertFalse(reduced)
    }

    @Test
    fun `an animation scale of zero means reduced motion`() {
        setAnimatorScale(0f)
        var reduced by mutableStateOf(false)
        composeRule.setContent { reduced = rememberReducedMotion() }

        composeRule.waitForIdle()

        assertTrue(reduced)
    }
}
