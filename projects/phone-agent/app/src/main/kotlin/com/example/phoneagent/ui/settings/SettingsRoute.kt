package com.example.phoneagent.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                val accessFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    context.contentResolver.takePersistableUriPermission(uri, accessFlags)
                    viewModel.onEvent(SettingsUiEvent.SaveSearchFolder(uri.toString()))
                } catch (_: SecurityException) {
                    viewModel.onEvent(SettingsUiEvent.SearchFolderPermissionUnavailable)
                }
            }
        }

    SettingsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onChooseFolder = { folderPicker.launch(null) },
        onBack = onBack,
        modifier = modifier,
    )
}
