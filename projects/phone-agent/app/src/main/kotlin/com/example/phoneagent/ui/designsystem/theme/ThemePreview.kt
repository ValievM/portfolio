package com.example.phoneagent.ui.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.example.phoneagent.R

@PreviewLightDark
@PreviewFontScale
@Composable
private fun ThemePreview() {
    AppTheme(useDynamicColor = false) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(AppTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
        ) {
            Text(
                text = stringResource(R.string.preview_cyrillic),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.preview_code),
                color = MaterialTheme.colorScheme.primary,
                style = TextStyle(fontFamily = AppMonospace),
            )
        }
    }
}
