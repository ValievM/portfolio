package com.example.phoneagent.ui.designsystem.components

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.example.phoneagent.ui.designsystem.theme.AppTheme

@PreviewLightDark
@PreviewFontScale
@Composable
private fun ScrollToBottomChipPreview() {
    AppTheme(useDynamicColor = false) {
        Surface {
            ScrollToBottomChip(onClick = {})
        }
    }
}
