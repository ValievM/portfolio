package com.example.phoneagent.ui.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.phoneagent.R
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.ui.designsystem.icons.AppIcons
import com.example.phoneagent.ui.designsystem.theme.AppCodeTextStyle
import com.example.phoneagent.ui.designsystem.theme.AppMotion
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private val prettyJson = Json { prettyPrint = true }

private fun JsonObject.toPrettyString(): String = prettyJson.encodeToString(JsonObject.serializer(), this)

private val StatusIconSize = 20.dp
private const val COLLAPSED_SUMMARY_LINES = 2
private const val CHEVRON_EXPANDED_DEGREES = 180f

/**
 * A tool call shown in the chat feed. Collapsed by default: the tool-defined [CallDescription] title
 * and summary plus a status pill. Expanding reveals the description details and the call arguments
 * (monospace). Everything rendered comes from structured data produced by the tool itself, never from
 * model text. The status is always an icon plus words, never a color alone.
 */
@Composable
fun ToolCallCard(
    state: ToolCallStatus,
    description: CallDescription,
    arguments: JsonObject,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
    modifier: Modifier = Modifier,
    fileHits: List<FileHit> = emptyList(),
    onOpenFile: (FileHit) -> Unit = {},
) {
    var selectedFile by remember { mutableStateOf<FileHit?>(null) }
    val shape = AppTheme.shapeTokens.card
    Surface(
        modifier = modifier.fillMaxWidth().animateContentSize(tween(AppMotion.EXPAND_MILLIS, easing = AppMotion.Standard)),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier =
                Modifier
                    .clip(shape)
                    .clickable(onClickLabel = stringResource(R.string.tool_call_toggle_arguments), onClick = onExpandToggle)
                    .padding(AppTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
        ) {
            ToolCallCardHeader(description = description, expanded = expanded)
            StatusPill(state)
            if (expanded) {
                ExpandedDetails(
                    description = description,
                    arguments = arguments,
                    fileHits = fileHits,
                    onFileHitClick = { selectedFile = it },
                )
            }
        }
    }
    selectedFile?.let { fileHit ->
        FileOpenConfirmationSheet(
            fileHit = fileHit,
            onOpen = {
                selectedFile = null
                onOpenFile(fileHit)
            },
            onDismiss = { selectedFile = null },
        )
    }
}

@Composable
private fun ToolCallCardHeader(
    description: CallDescription,
    expanded: Boolean,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) CHEVRON_EXPANDED_DEGREES else 0f,
        animationSpec = tween(AppMotion.EXPAND_MILLIS, easing = AppMotion.Standard),
        label = "chevron",
    )
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = description.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = description.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_SUMMARY_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painter = painterResource(AppIcons.expandMore),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(chevronRotation),
        )
    }
}

/** The tool-provided detail lines, then the call arguments in a horizontally scrollable code block. */
@Composable
private fun ExpandedDetails(
    description: CallDescription,
    arguments: JsonObject,
    fileHits: List<FileHit>,
    onFileHitClick: (FileHit) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        description.details.forEach { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.tool_call_arguments),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = arguments.toPrettyString(),
            style = AppCodeTextStyle,
            color = MaterialTheme.colorScheme.onSurface,
            softWrap = false,
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        )
        if (fileHits.isNotEmpty()) {
            Text(
                text = stringResource(R.string.files_found_heading),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            fileHits.forEach { fileHit -> FileHitRow(fileHit = fileHit, onClick = { onFileHitClick(fileHit) }) }
        }
    }
}

/** A found file: the whole name stays readable (it may wrap) and the row is a 48dp touch target. */
@Composable
private fun FileHitRow(
    fileHit: FileHit,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = fileHit.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = AppTheme.spacing.compact, vertical = AppTheme.spacing.compact),
        )
    }
}

/**
 * The status of the call as an icon plus words on the role color of that status (warning, primary,
 * success, error). Refused and interrupted are neutral, not errors: they are the user's decision.
 * A change of status cross-fades instead of jumping.
 */
@Composable
private fun StatusPill(state: ToolCallStatus) {
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            fadeIn(tween(AppMotion.STATUS_CROSSFADE_MILLIS)) togetherWith fadeOut(tween(AppMotion.STATUS_CROSSFADE_MILLIS))
        },
        label = "toolCallStatus",
    ) { status ->
        val look = status.look()
        val filled = look.container != Color.Transparent
        Row(
            modifier =
                Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .background(look.container)
                    .padding(
                        horizontal = if (filled) AppTheme.spacing.compact else 0.dp,
                        vertical = AppTheme.spacing.small,
                    ).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
        ) {
            StatusMark(status, look.content)
            Text(
                text = status.label(),
                style = MaterialTheme.typography.labelLarge,
                color = look.content,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

private class StatusLook(
    val container: Color,
    val content: Color,
)

@Composable
private fun ToolCallStatus.look(): StatusLook {
    val colors = MaterialTheme.colorScheme
    val extended = AppTheme.extendedColors
    return when (this) {
        ToolCallStatus.AwaitingApproval -> StatusLook(extended.warningContainer, extended.onWarningContainer)
        ToolCallStatus.Running -> StatusLook(colors.primaryContainer, colors.onPrimaryContainer)
        is ToolCallStatus.Done, is ToolCallStatus.FilesFound -> StatusLook(extended.successContainer, extended.onSuccessContainer)
        is ToolCallStatus.Failed -> StatusLook(colors.errorContainer, colors.onErrorContainer)
        ToolCallStatus.Rejected, ToolCallStatus.Cancelled -> StatusLook(Color.Transparent, colors.onSurfaceVariant)
    }
}

@Composable
private fun ToolCallStatus.label(): String =
    when (this) {
        ToolCallStatus.AwaitingApproval -> stringResource(R.string.tool_call_status_awaiting)
        ToolCallStatus.Running -> stringResource(R.string.tool_call_status_running)
        is ToolCallStatus.Done -> summary
        is ToolCallStatus.FilesFound -> stringResource(R.string.file_search_completed, count)
        is ToolCallStatus.Failed -> reason
        ToolCallStatus.Rejected -> stringResource(R.string.tool_call_status_rejected)
        ToolCallStatus.Cancelled -> stringResource(R.string.tool_call_status_cancelled)
    }

/** The status icon: a clock, an open ring while running, a tick, an exclamation mark, a crossed circle or a stop square. */
@Composable
private fun StatusMark(
    state: ToolCallStatus,
    tint: Color,
) {
    if (state == ToolCallStatus.Running) {
        CircularProgressIndicator(
            modifier = Modifier.size(StatusIconSize),
            strokeWidth = 2.dp,
            color = tint,
            trackColor = Color.Transparent,
        )
        return
    }
    val icon =
        when (state) {
            ToolCallStatus.AwaitingApproval -> AppIcons.hourglassEmpty
            is ToolCallStatus.Done, is ToolCallStatus.FilesFound -> AppIcons.checkCircle
            is ToolCallStatus.Failed -> AppIcons.error
            ToolCallStatus.Rejected -> AppIcons.block
            else -> AppIcons.stop
        }
    Icon(painter = painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(StatusIconSize))
}
