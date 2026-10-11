package com.example.phoneagent.ui.designsystem.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.example.phoneagent.R
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val PREVIEW_ALARM_HOUR = 7

private const val PREVIEW_ALARM_MINUTE = 30

private fun previewArguments() =
    buildJsonObject {
        put("hour", PREVIEW_ALARM_HOUR)
        put("minute", PREVIEW_ALARM_MINUTE)
        put("label", "Работа")
    }

@Composable
private fun previewApprovalRequest(): ApprovalRequest =
    ApprovalRequest(
        description =
            CallDescription(
                title = stringResource(R.string.preview_tool_call_title),
                summary = stringResource(R.string.preview_tool_call_summary),
                details = listOf(stringResource(R.string.preview_tool_call_details)),
            ),
        risk = Risk.CONFIRM,
        arguments = previewArguments(),
    )

@PreviewLightDark
@Composable
private fun ApprovalSheetPreview() {
    AppTheme(useDynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ApprovalSheet(
                request = previewApprovalRequest(),
                arguments = previewArguments(),
                onApprove = {},
                onReject = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ApprovalSheetDetailsExpandedPreview() {
    AppTheme(useDynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ApprovalSheet(
                request = previewApprovalRequest(),
                arguments = previewArguments(),
                onApprove = {},
                onReject = {},
                initialDetailsExpanded = true,
            )
        }
    }
}
