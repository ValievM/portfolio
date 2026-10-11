package com.example.phoneagent.ui.designsystem.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.theme.AppTheme

@Composable
private fun FileOpenConfirmationPreviewFrame() {
    AppTheme(useDynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            FileOpenConfirmationContent(
                fileHit =
                    FileHit(
                        name = stringResource(R.string.preview_file_name),
                        uri = "content://media/external/downloads/42",
                        size = 1_258_291L,
                        mime = "application/pdf",
                    ),
                onOpen = {},
                onDismiss = {},
            )
        }
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun FileOpenConfirmationPreview() {
    FileOpenConfirmationPreviewFrame()
}
