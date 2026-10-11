package com.example.phoneagent.ui.designsystem.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.theme.AppCodeTextStyle
import com.example.phoneagent.ui.designsystem.theme.AppTheme

/**
 * Confirmation before opening a link from a model reply. It is drawn from the parsed link
 * ([LinkDecision]), never from the reply text, so the domain shown is the real one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkConfirmationSheet(
    decision: LinkDecision,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConfirmationSheet(onDismissRequest = onDismiss, modifier = modifier) {
        LinkConfirmationContent(decision = decision, onOpen = onOpen, onDismiss = onDismiss)
    }
}

@Composable
fun LinkConfirmationContent(
    decision: LinkDecision,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SheetLayout(
        title =
            stringResource(
                if (decision is LinkDecision.Confirm) R.string.link_open_confirm_title else R.string.link_blocked_title,
            ),
        modifier = modifier,
        footer = {
            when (decision) {
                is LinkDecision.Confirm -> {
                    ButtonPair(
                        spacing = AppTheme.spacing.medium,
                        secondary = { SecondaryButton(onClick = onDismiss) { Text(stringResource(R.string.link_cancel_action)) } },
                        primary = { PrimaryButton(onClick = { onOpen(decision.url) }) { Text(stringResource(R.string.link_open_action)) } },
                    )
                }

                LinkDecision.Blocked -> {
                    PrimaryButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.link_close_action))
                    }
                }
            }
        },
    ) {
        when (decision) {
            is LinkDecision.Confirm -> {
                ConfirmDetails(decision)
            }

            LinkDecision.Blocked -> {
                Text(
                    text = stringResource(R.string.link_blocked_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ConfirmDetails(decision: LinkDecision.Confirm) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium)) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.extraSmall)) {
            Text(
                text = stringResource(R.string.link_open_confirm_domain),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = decision.host,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = decision.url,
            style = AppCodeTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.link_open_confirm_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
