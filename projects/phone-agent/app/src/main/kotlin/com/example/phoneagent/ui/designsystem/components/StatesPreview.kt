package com.example.phoneagent.ui.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.theme.AppTheme

@PreviewLightDark
@PreviewFontScale
@Composable
private fun StatusBannerPreview() {
    AppTheme(useDynamicColor = false) {
        Surface {
            Column {
                StatusBanner(message = stringResource(R.string.error_offline), onDismiss = {}, onRetry = {})
                StatusBanner(message = stringResource(R.string.error_session_expired), onDismiss = {})
            }
        }
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun InlineErrorPreview() {
    AppTheme(useDynamicColor = false) {
        Surface {
            Column(modifier = Modifier.padding(AppTheme.spacing.medium)) {
                InlineError(message = stringResource(R.string.error_timeout), onDismiss = {}, onRetry = {})
                InlineError(message = stringResource(R.string.error_forbidden), onDismiss = {})
            }
        }
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun EmptyStatePreview() {
    AppTheme(useDynamicColor = false) {
        Surface {
            EmptyState(
                suggestions =
                    listOf(
                        stringResource(R.string.chat_suggestion_alarm),
                        stringResource(R.string.chat_suggestion_find_file),
                        stringResource(R.string.chat_suggestion_notify),
                    ),
                onSuggestionClick = {},
            )
        }
    }
}
