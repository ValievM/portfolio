package com.example.phoneagent.ui.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** An error that belongs to the conversation, shown in the feed after the message it concerns. */
@Composable
fun InlineError(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    NoticeCard(message = message, onDismiss = onDismiss, modifier = modifier, onRetry = onRetry)
}
