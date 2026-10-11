package com.example.phoneagent.ui.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * System state above the composer: no network, session expired. Announced once to TalkBack when
 * it appears. [onRetry] is null when a retry would not help.
 */
@Composable
fun StatusBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    tone: NoticeTone = NoticeTone.Error,
) {
    NoticeCard(message = message, onDismiss = onDismiss, modifier = modifier, onRetry = onRetry, tone = tone)
}
