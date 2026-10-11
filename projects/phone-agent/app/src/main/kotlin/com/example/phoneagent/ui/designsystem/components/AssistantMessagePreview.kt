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

@Composable
private fun AssistantMessagePreviewFrame(text: String) {
    AppTheme(useDynamicColor = false) {
        Surface {
            Column(modifier = Modifier.padding(AppTheme.spacing.medium)) {
                AssistantMessage(text = text)
            }
        }
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun MarkdownReplyPreview() {
    AssistantMessagePreviewFrame(stringResource(R.string.preview_markdown_reply))
}

@PreviewLightDark
@Composable
private fun UnclosedCodeFencePreview() {
    AssistantMessagePreviewFrame(stringResource(R.string.preview_markdown_unclosed_fence))
}

@PreviewLightDark
@Composable
private fun ImageAndHtmlPreview() {
    AssistantMessagePreviewFrame(stringResource(R.string.preview_markdown_hostile))
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun LongWordPreview() {
    AssistantMessagePreviewFrame(stringResource(R.string.preview_cyrillic).repeat(LONG_WORD_REPEATS))
}

@PreviewLightDark
@Composable
private fun LinkConfirmationPreview() {
    AppTheme(useDynamicColor = false) {
        Surface {
            LinkConfirmationContent(
                decision = evaluateLink("https://accounts.google.com@evil.example/login?next=/settings"),
                onOpen = {},
                onDismiss = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun LinkBlockedPreview() {
    AppTheme(useDynamicColor = false) {
        Surface {
            LinkConfirmationContent(decision = LinkDecision.Blocked, onOpen = {}, onDismiss = {})
        }
    }
}

private const val LONG_WORD_REPEATS = 6
