package com.example.phoneagent.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.phoneagent.R
import com.example.phoneagent.domain.conversation.TurnState
import com.example.phoneagent.ui.designsystem.components.Composer
import com.example.phoneagent.ui.designsystem.components.ComposerMode
import com.example.phoneagent.ui.designsystem.components.FileHit
import com.example.phoneagent.ui.designsystem.components.InlineError
import com.example.phoneagent.ui.designsystem.components.StatusBanner
import com.example.phoneagent.ui.designsystem.components.ToolCallStatus
import com.example.phoneagent.ui.designsystem.icons.AppIcons
import com.example.phoneagent.ui.designsystem.layout.contentColumn
import com.example.phoneagent.ui.designsystem.layout.screenMargin
import com.example.phoneagent.ui.designsystem.theme.AppTheme

@Composable
fun ChatRoute(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ChatScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenSettings = onOpenSettings,
        onOpenFile = { fileHit -> openFileHit(context, fileHit) },
        onOpenLink = { url -> openLink(context, url) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onEvent: (ChatUiEvent) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenFile: (FileHit) -> Unit = {},
    onOpenLink: (String) -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        // The bottom is left to the composer: its bar runs under the navigation bar and rises with the keyboard.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chat_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(AppIcons.settings),
                            contentDescription = stringResource(R.string.open_settings),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        ChatBody(
            state = state,
            onEvent = onEvent,
            onOpenFile = onOpenFile,
            onOpenLink = onOpenLink,
            modifier = Modifier.padding(contentPadding),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatBody(
    state: ChatUiState,
    onEvent: (ChatUiEvent) -> Unit,
    onOpenFile: (FileHit) -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val error = state.errorForDisplay()?.toPresentation()
    val retry = if (state.turn.hasRetryAction()) ({ onEvent(ChatUiEvent.Retry) }) else null
    Column(modifier = modifier.fillMaxSize()) {
        MessageList(
            messages = state.messages,
            onEvent = onEvent,
            onOpenFile = onOpenFile,
            onOpenLink = onOpenLink,
            onSuggestionClick = { draft = it },
            preparing = state.isPreparingAnswer(),
            modifier = Modifier.weight(1f).imeNestedScroll(),
            inlineError =
                error?.takeIf { it.placement == ErrorPlacement.Inline }?.let { presentation ->
                    {
                        InlineError(
                            message = presentation.text(),
                            onDismiss = { onEvent(ChatUiEvent.DismissError) },
                            onRetry = retry,
                        )
                    }
                },
        )
        error?.takeIf { it.placement == ErrorPlacement.StatusBanner }?.let { presentation ->
            StatusBanner(
                message = presentation.text(),
                onDismiss = { onEvent(ChatUiEvent.DismissError) },
                onRetry = retry,
                modifier = Modifier.contentColumn().padding(horizontal = screenMargin(), vertical = AppTheme.spacing.small),
            )
        }
        Composer(
            draft = draft,
            onDraftChange = { draft = it },
            mode = if (state.turn.isInProgress()) ComposerMode.Running else ComposerMode.Ready,
            onSubmit = { onEvent(ChatUiEvent.Submit(it)) },
            onStop = { onEvent(ChatUiEvent.Stop) },
        )
    }
}

/**
 * "Готовлю ответ" is shown while the model is preparing or writing its text. While a tool call waits for
 * the user's decision or runs, that call's own card says so, so the indicator steps aside.
 */
private fun ChatUiState.isPreparingAnswer(): Boolean {
    val lastToolStatus = messages.lastOrNull()?.toolCall?.status
    return turn is TurnState.Running && lastToolStatus != ToolCallStatus.AwaitingApproval && lastToolStatus != ToolCallStatus.Running
}
