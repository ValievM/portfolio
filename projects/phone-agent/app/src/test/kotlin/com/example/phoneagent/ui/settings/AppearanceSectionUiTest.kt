package com.example.phoneagent.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.R
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
class AppearanceSectionUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<SettingsUiEvent>()
    private var state by mutableStateOf(SettingsUiState(isLoading = false))

    private fun string(id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun show() {
        composeRule.setContent {
            AppTheme(useDynamicColor = false) { AppearanceSection(state = state, onEvent = { events += it }) }
        }
    }

    @Test
    fun `the three theme modes are shown and only the saved one is selected`() {
        state = state.copy(themeMode = ThemeMode.LIGHT)
        show()

        composeRule.onNodeWithText(string(R.string.settings_theme_light)).assertIsSelected()
        composeRule.onNodeWithText(string(R.string.settings_theme_system)).assertIsNotSelected()
        composeRule.onNodeWithText(string(R.string.settings_theme_dark)).assertIsNotSelected()
    }

    @Test
    fun `choosing a mode reports exactly that mode`() {
        show()

        composeRule.onNodeWithText(string(R.string.settings_theme_dark)).performClick()

        assertEquals(listOf<SettingsUiEvent>(SettingsUiEvent.SelectThemeMode(ThemeMode.DARK)), events)
    }

    @Test
    fun `the system colors switch is shown from Android 12 and reports the new value`() {
        show()

        composeRule.onNodeWithText(string(R.string.settings_system_colors_title)).performClick()

        assertEquals(listOf<SettingsUiEvent>(SettingsUiEvent.SetSystemColors(true)), events)
    }

    @Test
    fun `controls are disabled until the saved settings are known`() {
        state = SettingsUiState(isLoading = true)
        show()

        composeRule.onNodeWithText(string(R.string.settings_theme_dark)).assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.settings_system_colors_title)).assertIsNotEnabled()
    }

    @Test
    fun `controls stay disabled when reading the settings failed`() {
        state = SettingsUiState(isLoading = false, settingsLoadFailed = true)
        show()

        composeRule.onNodeWithText(string(R.string.settings_theme_light)).assertIsNotEnabled()
    }

    @Test
    fun `a failed theme save is shown and the controls stay usable`() {
        state = state.copy(themeSaveFailed = true)
        show()

        composeRule.onNodeWithText(string(R.string.settings_theme_save_failed)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.settings_theme_dark)).assertIsEnabled()
    }

    @Test
    fun `no error is shown when the theme was saved`() {
        show()

        composeRule.onNodeWithText(string(R.string.settings_theme_save_failed)).assertDoesNotExist()
    }
}

/** Below Android 12 there are no system colors: the switch is not offered, the three modes still work. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], qualifiers = "w411dp-h891dp-port-xxhdpi")
class AppearanceSectionOldAndroidUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the system colors switch is hidden but the modes remain`() {
        composeRule.setContent {
            AppTheme(useDynamicColor = false) { AppearanceSection(state = SettingsUiState(isLoading = false), onEvent = {}) }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()

        composeRule.onNodeWithText(context.getString(R.string.settings_system_colors_title)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.settings_theme_dark)).assertIsDisplayed()
    }
}
