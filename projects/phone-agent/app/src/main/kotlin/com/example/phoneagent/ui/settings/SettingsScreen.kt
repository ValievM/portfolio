package com.example.phoneagent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.phoneagent.R
import com.example.phoneagent.auth.AppSignInMethod
import com.example.phoneagent.ui.designsystem.components.PrimaryButton
import com.example.phoneagent.ui.designsystem.components.QuietButton
import com.example.phoneagent.ui.designsystem.components.SecondaryButton
import com.example.phoneagent.ui.designsystem.components.StatusMessage
import com.example.phoneagent.ui.designsystem.icons.AppIcons
import com.example.phoneagent.ui.designsystem.layout.contentColumn
import com.example.phoneagent.ui.designsystem.layout.screenMargin
import com.example.phoneagent.ui.designsystem.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
    onChooseFolder: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showClearHistoryConfirmation by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(AppIcons.back),
                            contentDescription = stringResource(R.string.go_back),
                        )
                    }
                },
            )
        },
    ) { insets ->
        val direction = LocalLayoutDirection.current
        val margin = screenMargin()
        // The list scrolls under the system bars: the insets are content padding, not a frame around it.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = insets.calculateStartPadding(direction) + margin,
                    end = insets.calculateEndPadding(direction) + margin,
                    top = insets.calculateTopPadding() + AppTheme.spacing.small,
                    bottom = insets.calculateBottomPadding() + AppTheme.spacing.large,
                ),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
        ) {
            item { AccountSection(state, onEvent) }
            item { AppearanceSection(state, onEvent) }
            item { ModelSection(state, onEvent) }
            item { SearchFolderSection(state, onChooseFolder) }
            item { HistorySection(state) { showClearHistoryConfirmation = true } }
            if (state.settingsUnavailable) item { StatusMessage(stringResource(R.string.settings_unavailable), Modifier.contentColumn()) }
        }
    }

    if (showClearHistoryConfirmation) {
        ClearHistoryConfirmation(
            onConfirm = {
                showClearHistoryConfirmation = false
                onEvent(SettingsUiEvent.ClearHistory)
            },
            onDismiss = { showClearHistoryConfirmation = false },
        )
    }
}

@Composable
private fun AccountSection(
    state: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.settings_account_title)) {
        val accountText =
            when {
                state.isSignedIn -> state.signInMethod?.asStringResource() ?: R.string.settings_account_signed_in
                state.sessionExpired -> R.string.login_session_expired
                else -> R.string.settings_account_signed_out
            }
        Text(stringResource(accountText), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.isSignedIn) {
            SecondaryButton(onClick = { onEvent(SettingsUiEvent.SignOut) }) {
                Text(stringResource(R.string.sign_out))
            }
        }
    }
}

@Composable
private fun ModelSection(
    state: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    SettingsSection(title = stringResource(R.string.settings_model_title)) {
        Text(stringResource(R.string.settings_model_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            SecondaryButton(
                onClick = { expanded = true },
                enabled = state.canEditSettings && !state.isLoadingModels,
            ) {
                Text(state.model ?: stringResource(R.string.settings_model_default))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ModelMenuItem(R.string.settings_model_default, null, onEvent) { expanded = false }
                state.availableModels.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model) },
                        onClick = {
                            expanded = false
                            onEvent(SettingsUiEvent.SelectModel(model))
                        },
                    )
                }
            }
        }
        when {
            state.isLoadingModels -> {
                Text(stringResource(R.string.settings_models_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            state.modelCatalogUnavailable -> {
                Text(stringResource(R.string.settings_models_unavailable), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ModelMenuItem(
    label: Int,
    model: String?,
    onEvent: (SettingsUiEvent) -> Unit,
    onSelect: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        onClick = {
            onSelect()
            onEvent(SettingsUiEvent.SelectModel(model))
        },
    )
}

@Composable
private fun SearchFolderSection(
    state: SettingsUiState,
    onChooseFolder: () -> Unit,
) {
    SettingsSection(title = stringResource(R.string.settings_search_folder_title)) {
        val status =
            if (state.searchFolderSelected) {
                R.string.settings_search_folder_selected
            } else {
                R.string.settings_search_folder_not_selected
            }
        val action =
            if (state.searchFolderSelected) {
                R.string.settings_search_folder_change
            } else {
                R.string.settings_search_folder_choose
            }
        Text(stringResource(status), color = MaterialTheme.colorScheme.onSurfaceVariant)
        PrimaryButton(onClick = onChooseFolder, enabled = state.canEditSettings) {
            Text(stringResource(action))
        }
        if (state.folderPermissionUnavailable) {
            Text(
                stringResource(R.string.settings_search_folder_permission_error),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun HistorySection(
    state: SettingsUiState,
    onClear: () -> Unit,
) {
    SettingsSection(title = stringResource(R.string.settings_history_title)) {
        Text(stringResource(R.string.settings_history_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        QuietButton(onClick = onClear, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            Text(stringResource(R.string.settings_history_clear))
        }
        if (state.historyClearUnavailable) {
            Text(stringResource(R.string.settings_history_clear_error), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ClearHistoryConfirmation(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_history_confirm_title)) },
        text = { Text(stringResource(R.string.settings_history_confirm_message)) },
        confirmButton = {
            QuietButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(R.string.settings_history_confirm_action))
            }
        },
        dismissButton = {
            QuietButton(onClick = onDismiss) {
                Text(stringResource(R.string.file_cancel_action))
            }
        },
    )
}

/** A group of settings on `surfaceContainer`; on a tablet or in landscape the groups keep the reading width. */
@Composable
internal fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.contentColumn(),
        shape = AppTheme.shapeTokens.card,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(AppTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.compact),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

private fun AppSignInMethod.asStringResource(): Int =
    when (this) {
        AppSignInMethod.Browser -> R.string.settings_account_browser
        AppSignInMethod.DeviceCode -> R.string.settings_account_device_code
        AppSignInMethod.ApiKey -> R.string.settings_account_api_key
    }
