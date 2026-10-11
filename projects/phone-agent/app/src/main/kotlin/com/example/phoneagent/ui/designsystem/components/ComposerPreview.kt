package com.example.phoneagent.ui.designsystem.components

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.theme.AppTheme

@PreviewLightDark
@PreviewFontScale
@Composable
private fun IdleComposerPreview() {
    AppTheme(useDynamicColor = false) {
        Surface {
            Composer(draft = "", onDraftChange = {}, mode = ComposerMode.Ready, onSubmit = {}, onStop = {})
        }
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun TypedComposerPreview() {
    AppTheme(useDynamicColor = false) {
        Surface {
            Composer(
                draft = stringResource(R.string.chat_suggestion_alarm),
                onDraftChange = {},
                mode = ComposerMode.Ready,
                onSubmit = {},
                onStop = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun RunningComposerPreview() {
    AppTheme(useDynamicColor = false) {
        Surface {
            Composer(draft = "", onDraftChange = {}, mode = ComposerMode.Running, onSubmit = {}, onStop = {})
        }
    }
}

@PreviewLightDark
@Composable
private fun UnavailableComposerPreview() {
    AppTheme(useDynamicColor = false) {
        Surface {
            Composer(draft = "", onDraftChange = {}, mode = ComposerMode.Unavailable, onSubmit = {}, onStop = {})
        }
    }
}
