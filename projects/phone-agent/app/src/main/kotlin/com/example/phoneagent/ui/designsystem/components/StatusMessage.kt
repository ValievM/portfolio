package com.example.phoneagent.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.phoneagent.ui.designsystem.icons.AppIcons
import com.example.phoneagent.ui.designsystem.theme.AppTheme

private val MessageIconSize = 20.dp

/**
 * A short message inside a screen that the user does not dismiss (a failed save, an expired session):
 * icon and the whole text on the container role of its [tone]. For a message that appears on top of a
 * running screen and can be closed, see [NoticeCard].
 */
@Composable
fun StatusMessage(
    message: String,
    modifier: Modifier = Modifier,
    tone: NoticeTone = NoticeTone.Error,
) {
    val colors = MaterialTheme.colorScheme
    val extended = AppTheme.extendedColors
    val (container, content) =
        when (tone) {
            NoticeTone.Error -> colors.errorContainer to colors.onErrorContainer
            NoticeTone.Warning -> extended.warningContainer to extended.onWarningContainer
        }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppTheme.shapeTokens.card,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(AppTheme.spacing.medium),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.compact),
        ) {
            Icon(
                painter = painterResource(if (tone == NoticeTone.Error) AppIcons.error else AppIcons.hourglassEmpty),
                contentDescription = null,
                modifier = Modifier.size(MessageIconSize),
            )
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}
