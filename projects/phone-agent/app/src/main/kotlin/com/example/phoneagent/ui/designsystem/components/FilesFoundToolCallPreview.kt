package com.example.phoneagent.ui.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.example.phoneagent.R
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import kotlinx.serialization.json.buildJsonObject

@PreviewLightDark
@PreviewFontScale
@Composable
private fun FilesFoundToolCallPreview() {
    AppTheme(useDynamicColor = false) {
        ToolCallCard(
            state = ToolCallStatus.FilesFound(2),
            description =
                CallDescription(
                    title = stringResource(R.string.preview_files_found_title),
                    summary = stringResource(R.string.preview_files_found_summary),
                ),
            arguments = buildJsonObject {},
            expanded = true,
            onExpandToggle = {},
            fileHits =
                listOf(
                    FileHit(
                        name = "Договор.pdf",
                        uri = "content://media/external/file/1",
                        size = 1_258_291L,
                        mime = "application/pdf",
                    ),
                    FileHit(
                        name = "scan.jpg",
                        uri = "content://media/external/file/2",
                        size = 842_137L,
                        mime = "image/jpeg",
                    ),
                ),
        )
    }
}
