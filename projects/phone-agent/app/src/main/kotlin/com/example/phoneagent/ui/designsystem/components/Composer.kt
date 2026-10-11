package com.example.phoneagent.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.icons.AppIcons
import com.example.phoneagent.ui.designsystem.layout.contentColumn
import com.example.phoneagent.ui.designsystem.theme.AppTheme

/** What the composer can do right now. */
enum class ComposerMode {
    /** The user can type and send. */
    Ready,

    /** A reply is being generated: typing is off and the button stops it. */
    Running,

    /** Neither typing nor stopping makes sense (for example no network or not signed in). */
    Unavailable,
}

private val ComposerMinHeight = 56.dp

/**
 * Message input. While the mode is [ComposerMode.Running] the text field is disabled and the button turns into "stop".
 * The [draft] is owned by the caller (it also fills it from the empty-chat suggestions) and is
 * expected to survive configuration changes and process death via `rememberSaveable`.
 *
 * The bar is edge to edge (its background runs under the navigation bar) while the field itself stays
 * inside the reading column; it rises with the keyboard and grows up to six lines.
 */
@Composable
fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    mode: ComposerMode,
    onSubmit: (String) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val enabled = mode == ComposerMode.Ready
    val isRunning = mode == ComposerMode.Running
    val submit = {
        if (draft.isNotBlank() && enabled) {
            onSubmit(draft)
            onDraftChange("")
            keyboardController?.hide()
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier =
                Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
                    .contentColumn()
                    .padding(horizontal = AppTheme.spacing.medium, vertical = AppTheme.spacing.small),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                shape = AppTheme.shapeTokens.composer,
                colors = composerFieldColors(),
                placeholder = { Text(stringResource(R.string.message_hint)) },
                maxLines = COMPOSER_MAX_LINES,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
            )
            FilledIconButton(
                onClick = { if (isRunning) onStop() else submit() },
                modifier = Modifier.size(ComposerMinHeight),
                shape = AppTheme.shapeTokens.button,
                enabled = isRunning || (enabled && draft.isNotBlank()),
            ) {
                Icon(
                    painter = painterResource(if (isRunning) AppIcons.stop else AppIcons.send),
                    contentDescription =
                        stringResource(
                            if (isRunning) R.string.stop_generation else R.string.send_message,
                        ),
                )
            }
        }
    }
}

/** The field sits on `surfaceContainerHigh`; its border and focus ring are `outline` (1dp, 2dp when focused). */
@Composable
private fun composerFieldColors() =
    MaterialTheme.colorScheme.let { colors ->
        OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceContainerHigh,
            unfocusedContainerColor = colors.surfaceContainerHigh,
            disabledContainerColor = colors.surfaceContainerHigh,
            focusedBorderColor = colors.outline,
            unfocusedBorderColor = colors.outline,
            focusedPlaceholderColor = colors.onSurfaceVariant,
            unfocusedPlaceholderColor = colors.onSurfaceVariant,
        )
    }

private const val COMPOSER_MAX_LINES = 6
