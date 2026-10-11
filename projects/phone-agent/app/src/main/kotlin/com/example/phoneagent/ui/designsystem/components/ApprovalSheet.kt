package com.example.phoneagent.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.phoneagent.R
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.ui.designsystem.icons.AppIcons
import com.example.phoneagent.ui.designsystem.theme.AppCodeTextStyle
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private val prettyJson = Json { prettyPrint = true }

private fun JsonObject.toPrettyString(): String = prettyJson.encodeToString(JsonObject.serializer(), this)

/**
 * Confirmation sheet for consequential tool calls. Everything shown comes from the
 * [ApprovalRequest] description built by the tool itself (structured data), so the model
 * cannot fake a confirmation inside its reply. Focus lands on the title, not on
 * "Разрешить", and closing the sheet by back gesture or swipe counts as rejection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalSheet(
    request: ApprovalRequest,
    arguments: JsonObject,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
    initialDetailsExpanded: Boolean = false,
) {
    if (request.risk == Risk.SENSITIVE) {
        SensitiveApprovalSheet(request = request, onApprove = onApprove, onReject = onReject, modifier = modifier)
    } else {
        ConfirmationSheet(
            onDismissRequest = onReject,
            modifier = modifier,
        ) {
            SheetContent(
                request = request,
                arguments = arguments,
                onApprove = onApprove,
                onReject = onReject,
                initialDetailsExpanded = initialDetailsExpanded,
            )
        }
    }
}

@Composable
private fun SheetContent(
    request: ApprovalRequest,
    arguments: JsonObject,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    initialDetailsExpanded: Boolean,
) {
    var detailsExpanded by rememberSaveable { mutableStateOf(initialDetailsExpanded) }
    SheetLayout(
        title = stringResource(R.string.approval_sheet_title),
        footer = {
            ButtonPair(
                spacing = AppTheme.spacing.medium,
                secondary = { SecondaryButton(onClick = onReject) { Text(stringResource(R.string.approval_reject)) } },
                primary = { PrimaryButton(onClick = onApprove) { Text(stringResource(R.string.approval_approve)) } },
            )
        },
    ) {
        DescriptionBlock(description = request.description)
        QuietButton(onClick = { detailsExpanded = !detailsExpanded }) {
            Text(stringResource(R.string.approval_sheet_details))
            Icon(
                painter = painterResource(AppIcons.expandMore),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (detailsExpanded) {
            ExpandedDetails(
                arguments = arguments,
                details = request.description.details,
            )
        }
    }
}

@Composable
private fun DescriptionBlock(description: CallDescription) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.extraSmall)) {
        Text(
            text = description.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = description.summary,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ExpandedDetails(
    arguments: JsonObject,
    details: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.extraSmall)) {
        Text(
            text = arguments.toPrettyString(),
            style = AppCodeTextStyle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        details.forEach { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
