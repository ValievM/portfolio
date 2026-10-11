package com.example.phoneagent.screenshot

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.ui.designsystem.components.NoticeTone
import com.example.phoneagent.ui.designsystem.components.PreparingIndicator
import com.example.phoneagent.ui.designsystem.components.StatusBanner
import com.example.phoneagent.ui.login.LoginError
import com.example.phoneagent.ui.login.LoginFormState
import com.example.phoneagent.ui.login.LoginScreen
import com.example.phoneagent.ui.login.LoginUiState
import com.example.phoneagent.ui.root.ThemeState
import com.example.phoneagent.ui.root.ThemedRoot
import com.example.phoneagent.ui.settings.SettingsScreen
import com.example.phoneagent.ui.settings.SettingsUiState
import com.example.phoneagent.ui.settings.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val allVariants = listOf(Variant.Light, Variant.Dark, Variant.LargeFont)

/** Login, settings, the app root and the "preparing" indicator in every state (docs/design/qa.md matrix). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PHONE_QUALIFIERS)
class ScreensScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun login(
        name: String,
        state: LoginUiState = LoginUiState(),
        form: LoginFormState = LoginFormState(),
    ) = composeRule.snapshot("login_$name", allVariants, fillScreen = true) {
        LoginScreen(state = state, form = form, onEvent = {})
    }

    @Test
    fun loginSignedOut() = login("signed_out")

    @Test
    fun loginApiKey() = login("api_key", form = LoginFormState(showApiKey = true, apiKey = "sk-test"))

    @Test
    fun loginError() = login("error", LoginUiState(error = LoginError.NETWORK, showDeviceCodeFallback = true))

    @Test
    fun loginSessionExpired() = login("session_expired", LoginUiState(sessionExpired = true))

    @Test
    fun loginDeviceCode() =
        login("device_code", LoginUiState(deviceCode = "ABCD-EFGH", deviceVerificationUrl = "https://auth.example/device"))

    private fun settings(
        name: String,
        state: SettingsUiState,
    ) = composeRule.snapshot("settings_$name", allVariants, fillScreen = true) {
        SettingsScreen(state = state, onEvent = {}, onChooseFolder = {}, onBack = {})
    }

    private val loaded =
        SettingsUiState(
            isLoading = false,
            isLoadingModels = false,
            model = "gpt-5.6-luna",
            availableModels = listOf("gpt-5.6-luna", "gpt-5.6-terra"),
            isSignedIn = true,
        )

    @Test
    fun settingsLoaded() = settings("loaded", loaded)

    @Test
    fun settingsDarkChosen() = settings("dark_chosen", loaded.copy(themeMode = ThemeMode.DARK, searchFolderSelected = true))

    @Test
    fun settingsLoading() = settings("loading", SettingsUiState())

    @Test
    fun settingsLoadFailed() =
        settings("load_failed", SettingsUiState(isLoading = false, settingsUnavailable = true, settingsLoadFailed = true))

    @Test
    fun settingsThemeSaveFailed() = settings("theme_save_failed", loaded.copy(themeSaveFailed = true))

    @Test
    fun rootLoading() =
        composeRule.snapshot("root_loading", listOf(Variant.Light, Variant.Dark), fillScreen = true) {
            ThemedRoot(state = ThemeState.Loading, onRetry = {}) {}
        }

    @Test
    fun rootFirstReadFailed() =
        composeRule.snapshot("root_first_read_failed", allVariants, fillScreen = true) {
            ThemedRoot(state = ThemeState.Unavailable(lastKnown = null), onRetry = {}) {}
        }

    @Test
    fun preparingIndicator() =
        composeRule.snapshot("preparing_indicator", listOf(Variant.Light, Variant.Dark, Variant.LargeFont)) {
            PreparingIndicator(animated = false)
        }

    @Test
    fun warningBanner() =
        composeRule.snapshot("banner_warning", allVariants) {
            StatusBanner(message = "Не удалось обновить настройки. Показана последняя сохранённая тема.", onDismiss = {
            }, onRetry = {}, tone = NoticeTone.Warning)
        }
}
