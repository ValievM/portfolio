package com.example.phoneagent.ui.root

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.example.phoneagent.R
import com.example.phoneagent.domain.storage.AppSettings
import com.example.phoneagent.ui.designsystem.theme.AppTheme

@Composable
private fun RootFrame(state: ThemeState) {
    AppTheme(useDynamicColor = false) {
        ThemedRoot(state = state, onRetry = {}) { Text(stringResource(R.string.preview_assistant_message)) }
    }
}

@PreviewLightDark
@Composable
private fun RootLoadingPreview() = RootFrame(ThemeState.Loading)

@PreviewLightDark
@PreviewFontScale
@Composable
private fun RootFirstReadFailedPreview() = RootFrame(ThemeState.Unavailable(lastKnown = null))

@PreviewLightDark
@Composable
private fun RootStaleSettingsPreview() = RootFrame(ThemeState.Unavailable(lastKnown = AppSettings(useDarkTheme = true)))
