package com.example.phoneagent.ui.designsystem.components

import android.text.format.Formatter
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FileOpenConfirmationSheet(
    fileHit: FileHit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmationSheet(onDismissRequest = onDismiss) {
        FileOpenConfirmationContent(
            fileHit = fileHit,
            onOpen = onOpen,
            onDismiss = onDismiss,
        )
    }
}

@Composable
internal fun FileOpenConfirmationContent(
    fileHit: FileHit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sizeLabel =
        fileHit.size?.let { size ->
            stringResource(R.string.file_size_format, Formatter.formatShortFileSize(context, size))
        } ?: stringResource(R.string.file_size_unknown)

    SheetLayout(
        title = stringResource(R.string.file_open_confirm_title),
        footer = {
            ButtonPair(
                spacing = AppTheme.spacing.medium,
                secondary = { SecondaryButton(onClick = onDismiss) { Text(stringResource(R.string.file_cancel_action)) } },
                primary = { PrimaryButton(onClick = onOpen) { Text(stringResource(R.string.file_open_action)) } },
            )
        },
    ) {
        Text(
            text = fileHit.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = sizeLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
