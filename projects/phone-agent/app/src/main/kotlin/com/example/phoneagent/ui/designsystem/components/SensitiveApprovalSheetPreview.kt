package com.example.phoneagent.ui.designsystem.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import kotlinx.serialization.json.buildJsonObject

private val sensitivePreviewRequest =
    ApprovalRequest(
        description =
            CallDescription(
                title = "Отправить SMS",
                summary = "Адресат: Иван Петров, +7 999 123-45-67",
                details = listOf("Текст: Встречаемся у главного входа в четверг в 18:30."),
            ),
        risk = Risk.SENSITIVE,
        arguments = buildJsonObject {},
    )

@PreviewLightDark
@Composable
private fun SensitiveApprovalSheetPreview() {
    AppTheme(useDynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            SensitiveApprovalSheet(request = sensitivePreviewRequest, onApprove = {}, onReject = {})
        }
    }
}

@Preview(name = "Sensitive approval · 200%", widthDp = 320, heightDp = 640, fontScale = 2f)
@Composable
private fun SensitiveApprovalSheetLargeFontPreview() {
    AppTheme(useDynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            SensitiveApprovalSheet(request = sensitivePreviewRequest, onApprove = {}, onReject = {})
        }
    }
}
