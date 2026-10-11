package com.example.phoneagent.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.example.phoneagent.R
import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.conversation.ItemId
import com.example.phoneagent.domain.conversation.TurnState
import com.example.phoneagent.ui.designsystem.theme.AppTheme

@PreviewLightDark
@Composable
private fun IdleComposerPreview() {
    AppTheme(useDynamicColor = false) {
        ChatScreen(ChatUiState(), {}, {})
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun ConversationChatPreview() {
    AppTheme(useDynamicColor = false) {
        ChatScreen(
            state =
                ChatUiState(
                    messages =
                        listOf(
                            ChatMessage("1", ChatRole.USER, stringResource(R.string.preview_user_message)),
                            ChatMessage("2", ChatRole.ASSISTANT, stringResource(R.string.preview_assistant_message)),
                        ),
                ),
            onEvent = {},
            onOpenSettings = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun RunningComposerPreview() {
    AppTheme(useDynamicColor = false) {
        ChatScreen(ChatUiState(turn = TurnState.Running(ItemId("preview-question"))), {}, {})
    }
}

@PreviewLightDark
@Composable
private fun ErrorChatPreview() {
    AppTheme(useDynamicColor = false) {
        ChatScreen(ChatUiState(turn = TurnState.FailedRetryable(ItemId("preview-question"), AgentError.Offline)), {}, {})
    }
}
