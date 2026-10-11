package com.example.phoneagent.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.phoneagent.R
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.ui.designsystem.theme.AppTheme

private val NumberColumnWidth = 32.dp

/**
 * One confirmation for several calls of the same model response (e.g. five alarms). Every row comes
 * from the tool's own description ([ApprovalRequest.description]), never from the model's text, so
 * the user sees exactly what will run. The rows are numbered and complete (nothing is cut off with an
 * ellipsis), the list scrolls and the two buttons stay in place. It is all or nothing; back gesture or
 * swipe counts as "reject all", like [ApprovalSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchApprovalSheet(
    requests: List<ApprovalRequest>,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConfirmationSheet(
        onDismissRequest = onReject,
        modifier = modifier,
    ) {
        BatchSheetContent(requests = requests, onApprove = onApprove, onReject = onReject)
    }
}

@Composable
private fun BatchSheetContent(
    requests: List<ApprovalRequest>,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    SheetLayout(
        title = pluralStringResource(R.plurals.batch_approval_title, requests.size, requests.size),
        footer = {
            ButtonPair(
                spacing = AppTheme.spacing.medium,
                secondary = { SecondaryButton(onClick = onReject) { Text(stringResource(R.string.batch_approval_reject_all)) } },
                primary = {
                    PrimaryButton(onClick = onApprove) {
                        Text(stringResource(R.string.batch_approval_approve_all, requests.size))
                    }
                },
            )
        },
    ) {
        Column {
            requests.forEachIndexed { index, request ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                BatchRow(number = index + 1, request = request)
            }
        }
    }
}

@Composable
private fun BatchRow(
    number: Int,
    request: ApprovalRequest,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppTheme.spacing.compact),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = NumberColumnWidth),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.extraSmall)) {
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
        }
    }
}
