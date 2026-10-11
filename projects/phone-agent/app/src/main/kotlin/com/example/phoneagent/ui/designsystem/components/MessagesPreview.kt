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

private const val LONG_TEXT_REPEATS = 12

@PreviewLightDark
@PreviewFontScale
@Composable
private fun MessagesPreview() {
    AppTheme(useDynamicColor = false) {
        Surface {
            Column(modifier = Modifier.padding(AppTheme.spacing.medium)) {
                UserMessage(stringResource(R.string.preview_user_message))
                AssistantMessage(stringResource(R.string.preview_assistant_message))
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun LongMessagesPreview() {
    val longText = List(LONG_TEXT_REPEATS) { stringResource(R.string.preview_cyrillic) }.joinToString(separator = " ")
    AppTheme(useDynamicColor = false) {
        Surface {
            Column(modifier = Modifier.padding(AppTheme.spacing.medium)) {
                UserMessage(longText)
                AssistantMessage(longText)
            }
        }
    }
}
