package com.example.phoneagent.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.phoneagent.R
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.ui.designsystem.icons.AppIcons
import com.example.phoneagent.ui.designsystem.theme.AppTheme

/** A single sensitive action: its complete, tool-authored description is visible before approval. */
@Composable
fun SensitiveApprovalSheet(
    request: ApprovalRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConfirmationSheet(onDismissRequest = onReject, modifier = modifier) {
        SheetLayout(
            title = stringResource(R.string.sensitive_approval_title),
            footer = {
                ButtonPair(
                    spacing = AppTheme.spacing.medium,
                    secondary = { SecondaryButton(onClick = onReject) { Text(stringResource(R.string.approval_reject)) } },
                    primary = { PrimaryButton(onClick = onApprove) { Text(stringResource(R.string.approval_approve)) } },
                )
            },
        ) {
            SensitiveWarning()
            Text(
                text = request.description.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = request.description.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            request.description.details.forEach { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun SensitiveWarning() {
    val colors = AppTheme.extendedColors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppTheme.shapeTokens.card,
        color = colors.warningContainer,
        contentColor = colors.onWarningContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppTheme.spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.compact),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                painter = painterResource(AppIcons.warning),
                contentDescription = null,
                tint = colors.onWarningContainer,
                modifier = Modifier.padding(top = AppTheme.spacing.extraSmall).size(AppTheme.spacing.large),
            )
            Text(
                text = stringResource(R.string.sensitive_approval_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onWarningContainer,
            )
        }
    }
}
