package com.example.phoneagent.ui.designsystem.components

import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.icons.AppIcons

/** "↓ к новым": shown only while the reader is away from the bottom of the feed. */
@Composable
fun ScrollToBottomChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.scroll_to_new_messages_description)
    AssistChip(
        onClick = onClick,
        label = { Text(stringResource(R.string.scroll_to_new_messages)) },
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = AssistChipDefaults.assistChipElevation(elevation = 2.dp),
        modifier = modifier.semantics { contentDescription = description },
        leadingIcon = {
            Icon(
                painter = painterResource(AppIcons.arrowDownward),
                contentDescription = null,
            )
        },
    )
}
