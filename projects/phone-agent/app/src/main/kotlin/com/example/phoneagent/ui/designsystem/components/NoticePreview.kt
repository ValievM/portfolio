package com.example.phoneagent.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.theme.AppTheme

private const val PREVIEW_BATCH_SIZE = 15

@Composable
private fun PreviewFrame(content: @Composable () -> Unit) {
    AppTheme(useDynamicColor = false) {
        Column(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(AppTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
            content = { content() },
        )
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun NoticesPreview() =
    PreviewFrame {
        StatusBanner(message = stringResource(R.string.error_offline), onDismiss = {}, onRetry = {})
        StatusBanner(message = stringResource(R.string.theme_settings_stale), onDismiss = {}, tone = NoticeTone.Warning)
        InlineError(message = stringResource(R.string.error_timeout), onDismiss = {}, onRetry = {})
        StatusMessage(message = stringResource(R.string.settings_theme_save_failed))
        StatusMessage(message = stringResource(R.string.error_session_expired), tone = NoticeTone.Warning)
    }

@PreviewLightDark
@PreviewFontScale
@Composable
private fun PreparingIndicatorPreview() = PreviewFrame { PreparingIndicator(animated = false) }

@PreviewLightDark
@PreviewFontScale
@Composable
private fun ButtonPairAndButtonsPreview() =
    PreviewFrame {
        ButtonPair(
            spacing = AppTheme.spacing.medium,
            secondary = { SecondaryButton(onClick = {}) { Text(stringResource(R.string.batch_approval_reject_all)) } },
            primary = { PrimaryButton(onClick = {}) { Text(stringResource(R.string.batch_approval_approve_all, PREVIEW_BATCH_SIZE)) } },
        )
        QuietButton(onClick = {}) { Text(stringResource(R.string.error_retry)) }
    }
