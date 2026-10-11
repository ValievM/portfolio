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

private val PreviewAlarmTimes = listOf("06:00", "06:15", "06:30", "06:45", "07:00")

@Composable
private fun previewBatchRequests(): List<ApprovalRequest> =
    PreviewAlarmTimes.map { time ->
        ApprovalRequest(
            description =
                CallDescription(
                    title = stringResource(R.string.preview_tool_call_title),
                    summary = stringResource(R.string.preview_batch_alarm_summary, time),
                ),
            risk = Risk.CONFIRM,
            arguments = buildJsonObject {},
        )
    }

@PreviewLightDark
@Composable
private fun BatchApprovalSheetPreview() {
    AppTheme(useDynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            BatchApprovalSheet(
                requests = previewBatchRequests(),
                onApprove = {},
                onReject = {},
            )
        }
    }
}
