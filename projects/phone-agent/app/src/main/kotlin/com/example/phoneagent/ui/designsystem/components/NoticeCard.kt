package com.example.phoneagent.ui.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.icons.AppIcons
import com.example.phoneagent.ui.designsystem.theme.AppMotion
import com.example.phoneagent.ui.designsystem.theme.AppTheme

/** What a notice is about: [Error] is a failure, [Warning] is something that still works but needs attention. */
enum class NoticeTone { Error, Warning }

private val NoticeIconSize = 20.dp

/**
 * A message that belongs to the screen: an icon, the whole text (never cut with an ellipsis), an optional
 * "retry" and a close button. It fades in over 160ms without moving, and TalkBack reads it once when it
 * appears. The role color pair (container plus its "on" color) carries the tone; the icon and the words
 * carry the meaning for people who cannot tell the colors apart.
 */
@Composable
internal fun NoticeCard(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    tone: NoticeTone = NoticeTone.Error,
) {
    val colors = MaterialTheme.colorScheme
    val extended = AppTheme.extendedColors
    val (container, content) =
        when (tone) {
            NoticeTone.Error -> colors.errorContainer to colors.onErrorContainer
            NoticeTone.Warning -> extended.warningContainer to extended.onWarningContainer
        }
    val appearing = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = appearing,
        modifier = modifier.fillMaxWidth(),
        enter = fadeIn(tween(AppMotion.BANNER_APPEAR_MILLIS)),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            shape = AppTheme.shapeTokens.card,
            color = container,
            contentColor = content,
        ) {
            NoticeContent(message = message, contentColor = content, onDismiss = onDismiss, onRetry = onRetry)
        }
    }
}

@Composable
private fun NoticeContent(
    message: String,
    contentColor: Color,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.padding(start = AppTheme.spacing.medium, top = AppTheme.spacing.compact, bottom = AppTheme.spacing.small),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.compact),
    ) {
        Icon(
            painter = painterResource(AppIcons.error),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.padding(top = AppTheme.spacing.extraSmall).size(NoticeIconSize),
        )
        Column(modifier = Modifier.weight(1f).padding(top = AppTheme.spacing.extraSmall)) {
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = contentColor)
            onRetry?.let {
                QuietButton(onClick = it, contentPadding = PaddingValues(vertical = AppTheme.spacing.compact)) {
                    Text(stringResource(R.string.error_retry), color = contentColor)
                }
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(
                painter = painterResource(AppIcons.close),
                contentDescription = stringResource(R.string.dismiss_error),
                tint = contentColor,
            )
        }
    }
}
