package com.example.phoneagent.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.phoneagent.ui.designsystem.theme.AppTheme

private val MessageMaxWidth = 560.dp

/** The user's own message: right-aligned, on a tonal bubble. */
@Composable
fun UserMessage(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        // A user bubble never runs the full width: the gap on the left tells it from a model reply.
        modifier = modifier.fillMaxWidth().padding(start = AppTheme.spacing.extraLarge),
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = text,
            modifier =
                Modifier
                    .widthIn(max = MessageMaxWidth)
                    .clip(AppTheme.shapeTokens.userBubble)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(
                        horizontal = AppTheme.spacing.medium,
                        vertical = AppTheme.spacing.compact,
                    ),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/**
 * The model's reply: no bubble, full width, rendered as markdown. A tapped link never opens on its
 * own: it goes through [LinkConfirmationSheet] first and only then reaches [onOpenLink].
 */
@Composable
fun AssistantMessage(
    text: String,
    modifier: Modifier = Modifier,
    onOpenLink: (String) -> Unit = {},
) {
    var pendingLink by remember { mutableStateOf<LinkDecision?>(null) }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        AssistantMarkdown(
            text = text,
            onLinkClick = { url -> pendingLink = evaluateLink(url) },
            modifier = Modifier.widthIn(max = MessageMaxWidth),
        )
    }
    pendingLink?.let { decision ->
        LinkConfirmationSheet(
            decision = decision,
            onOpen = { url ->
                pendingLink = null
                onOpenLink(url)
            },
            onDismiss = { pendingLink = null },
        )
    }
}
