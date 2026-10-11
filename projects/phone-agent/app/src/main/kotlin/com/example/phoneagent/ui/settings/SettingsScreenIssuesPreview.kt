package com.example.phoneagent.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark

@PreviewLightDark
@Composable
private fun SettingsModelCatalogLoadingPreview() =
    SettingsPreviewFrame(ReadyState.copy(availableModels = emptyList(), isLoadingModels = true))

@PreviewLightDark
@Composable
private fun SettingsModelCatalogUnavailablePreview() =
    SettingsPreviewFrame(ReadyState.copy(availableModels = emptyList(), isLoadingModels = false, modelCatalogUnavailable = true))

@PreviewLightDark
@Composable
private fun SettingsFolderNotSelectedPreview() = SettingsPreviewFrame(ReadyState.copy(searchFolderSelected = false))

@PreviewLightDark
@Composable
private fun SettingsFolderPermissionUnavailablePreview() =
    SettingsPreviewFrame(ReadyState.copy(searchFolderSelected = false, folderPermissionUnavailable = true))

@PreviewLightDark
@Composable
private fun SettingsHistoryClearUnavailablePreview() = SettingsPreviewFrame(ReadyState.copy(historyClearUnavailable = true))

@PreviewLightDark
@Composable
private fun SettingsUnavailablePreview() = SettingsPreviewFrame(ReadyState.copy(settingsUnavailable = true))

@PreviewLightDark
@Composable
private fun SettingsThemeSaveFailedPreview() = SettingsPreviewFrame(ReadyState.copy(themeSaveFailed = true))

@PreviewLightDark
@Composable
private fun SettingsLoadFailedPreview() =
    SettingsPreviewFrame(SettingsUiState(isLoading = false, settingsUnavailable = true, settingsLoadFailed = true))

@PreviewLightDark
@Composable
private fun SettingsDarkThemeChosenPreview() = SettingsPreviewFrame(ReadyState.copy(themeMode = ThemeMode.DARK))
