package com.example.phoneagent.ui.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.example.phoneagent.R
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Composable
private fun previewDescription(): CallDescription =
    CallDescription(
        title = stringResource(R.string.preview_tool_call_title),
        summary = stringResource(R.string.preview_tool_call_summary),
        details = listOf(stringResource(R.string.preview_tool_call_details)),
    )

private const val PREVIEW_ALARM_HOUR = 7

private const val PREVIEW_ALARM_MINUTE = 30

private fun previewArguments() =
    buildJsonObject {
        put("hour", PREVIEW_ALARM_HOUR)
        put("minute", PREVIEW_ALARM_MINUTE)
        put("label", "Работа")
    }

@Composable
private fun ToolCallCardPreviewFrame(
    state: ToolCallStatus,
    expanded: Boolean = false,
    fileHits: List<FileHit> = emptyList(),
    description: CallDescription? = null,
) {
    AppTheme(useDynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(AppTheme.spacing.large)) {
                ToolCallCard(
                    state = state,
                    description = description ?: previewDescription(),
                    arguments = previewArguments(),
                    expanded = expanded,
                    onExpandToggle = {},
                    fileHits = fileHits,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ToolCallCardAwaitingApprovalPreview() {
    ToolCallCardPreviewFrame(ToolCallStatus.AwaitingApproval)
}

@PreviewLightDark
@Composable
private fun ToolCallCardRunningPreview() {
    ToolCallCardPreviewFrame(ToolCallStatus.Running)
}

@PreviewLightDark
@Composable
private fun ToolCallCardDonePreview() {
    ToolCallCardPreviewFrame(ToolCallStatus.Done(summary = stringResource(R.string.preview_tool_call_result)))
}

@PreviewLightDark
@Composable
private fun ToolCallCardFailedPreview() {
    ToolCallCardPreviewFrame(ToolCallStatus.Failed(reason = stringResource(R.string.preview_tool_call_reason)))
}

@PreviewLightDark
@Composable
private fun ToolCallCardRejectedPreview() {
    ToolCallCardPreviewFrame(ToolCallStatus.Rejected)
}

@PreviewLightDark
@Composable
private fun ToolCallCardCancelledPreview() {
    ToolCallCardPreviewFrame(ToolCallStatus.Cancelled)
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun ToolCallCardExpandedPreview() {
    ToolCallCardPreviewFrame(
        ToolCallStatus.Done(summary = stringResource(R.string.preview_tool_call_result)),
        expanded = true,
    )
}
