package com.example.phoneagent.ui.settings

import com.example.phoneagent.auth.AppSignInMethod

sealed interface SettingsUiEvent {
    data class SelectModel(
        val model: String?,
    ) : SettingsUiEvent

    data class SelectThemeMode(
        val mode: ThemeMode,
    ) : SettingsUiEvent

    data class SetSystemColors(
        val enabled: Boolean,
    ) : SettingsUiEvent

    data object SignOut : SettingsUiEvent

    data class SaveSearchFolder(
        val uri: String?,
    ) : SettingsUiEvent

    data object ClearHistory : SettingsUiEvent

    data object SearchFolderPermissionUnavailable : SettingsUiEvent
}

data class SettingsUiState(
    val isLoading: Boolean = true,
    val model: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useSystemColors: Boolean = false,
    val availableModels: List<String> = emptyList(),
    val modelCatalogUnavailable: Boolean = false,
    val isLoadingModels: Boolean = true,
    val searchFolderSelected: Boolean = false,
    val folderPermissionUnavailable: Boolean = false,
    val historyClearUnavailable: Boolean = false,
    val settingsUnavailable: Boolean = false,
    /** Reading the saved settings failed: nothing may be written, or defaults would overwrite the model and folder. */
    val settingsLoadFailed: Boolean = false,
    val themeSaveFailed: Boolean = false,
    val isSignedIn: Boolean = false,
    val sessionExpired: Boolean = false,
    val signInMethod: AppSignInMethod? = null,
)

/** Editing is off until the saved settings are known, and stays off if reading them failed. */
val SettingsUiState.canEditSettings: Boolean
    get() = !isLoading && !settingsLoadFailed
