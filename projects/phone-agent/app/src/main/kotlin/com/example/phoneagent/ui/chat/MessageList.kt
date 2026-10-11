package com.example.phoneagent.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.components.AssistantMessage
import com.example.phoneagent.ui.designsystem.components.EmptyState
import com.example.phoneagent.ui.designsystem.components.FileHit
import com.example.phoneagent.ui.designsystem.components.PreparingIndicator
import com.example.phoneagent.ui.designsystem.components.ScrollToBottomChip
import com.example.phoneagent.ui.designsystem.components.ToolCallCard
import com.example.phoneagent.ui.designsystem.components.UserMessage
import com.example.phoneagent.ui.designsystem.layout.contentColumn
import com.example.phoneagent.ui.designsystem.layout.screenMargin
import com.example.phoneagent.ui.designsystem.theme.AppMotion
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import kotlinx.coroutines.launch

internal object ChatTestTags {
    const val MESSAGE_LIST = "message_list"
    const val SCROLL_TO_BOTTOM_CHIP = "scroll_to_bottom_chip"
}

@Composable
internal fun MessageList(
    messages: List<ChatMessage>,
    onEvent: (ChatUiEvent) -> Unit,
    onOpenFile: (FileHit) -> Unit,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenLink: (String) -> Unit = {},
    preparing: Boolean = false,
    inlineError: (@Composable () -> Unit)? = null,
) {
    if (messages.isEmpty()) {
        // An error can happen before the first message (e.g. history failed to load); the inline
        // error must be visible here too, not only inside a non-empty feed.
        Column(modifier = modifier) {
            EmptyState(
                suggestions =
                    listOf(
                        stringResource(R.string.chat_suggestion_alarm),
                        stringResource(R.string.chat_suggestion_find_file),
                        stringResource(R.string.chat_suggestion_notify),
                    ),
                onSuggestionClick = onSuggestionClick,
                modifier = Modifier.weight(1f),
            )
            if (preparing) PreparingIndicator(Modifier.padding(horizontal = screenMargin(), vertical = AppTheme.spacing.small))
            inlineError?.invoke()
        }
    } else {
        FollowingFeed(messages, FeedActions(onEvent, onOpenFile, onOpenLink), preparing, inlineError, modifier)
    }
}

/**
 * The feed follows new messages only if the reader is at the bottom (or just sent one themselves);
 * when they are reading older messages it stays put and shows [ScrollToBottomChip] instead.
 */
@Composable
private fun FollowingFeed(
    messages: List<ChatMessage>,
    actions: FeedActions,
    preparing: Boolean,
    inlineError: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val tolerancePx = with(LocalDensity.current) { AT_BOTTOM_TOLERANCE.roundToPx() }
    val atBottom by remember(listState, tolerancePx) {
        derivedStateOf {
            isAtBottom(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, tolerancePx)
        }
    }
    var hasUnseenMessages by remember { mutableStateOf(false) }

    val newest = messages.last()
    var lastNewestId by remember { mutableStateOf(newest.id) }
    // `atBottom` is read while composing the frame in which the new message appears, i.e. before
    // the list has been measured with it, so it still describes where the reader was.
    val wasAtBottom = atBottom
    LaunchedEffect(newest.id) {
        if (lastNewestId != newest.id) {
            lastNewestId = newest.id
            val origin = if (newest.role == ChatRole.USER) NewMessageOrigin.User else NewMessageOrigin.Model
            when (feedScrollActionForNewMessage(wasAtBottom, origin)) {
                FeedScrollAction.ScrollToBottom -> listState.animateScrollToItem(0)
                FeedScrollAction.ShowNewMessagesChip -> hasUnseenMessages = true
            }
        }
    }
    LaunchedEffect(atBottom) {
        if (atBottom) hasUnseenMessages = false
    }

    Box(modifier = modifier.fillMaxWidth()) {
        FeedColumn(messages, actions, preparing, inlineError, listState)
        AnimatedVisibility(
            visible = isNewMessagesChipVisible(atBottom, hasUnseenMessages),
            modifier = Modifier.align(Alignment.BottomEnd).padding(AppTheme.spacing.medium),
            enter =
                fadeIn(tween(AppMotion.STATUS_CROSSFADE_MILLIS)) + scaleIn(tween(AppMotion.STATUS_CROSSFADE_MILLIS), initialScale = 0.9f),
            exit = fadeOut(tween(AppMotion.STATUS_CROSSFADE_MILLIS)),
        ) {
            ScrollToBottomChip(
                onClick = {
                    hasUnseenMessages = false
                    scope.launch { listState.animateScrollToItem(0) }
                },
                modifier = Modifier.testTag(ChatTestTags.SCROLL_TO_BOTTOM_CHIP),
            )
        }
    }
}

/** What a message in the feed can ask the screen to do. */
private class FeedActions(
    val onEvent: (ChatUiEvent) -> Unit,
    val onOpenFile: (FileHit) -> Unit,
    val onOpenLink: (String) -> Unit,
)

/**
 * The reversed list: the newest item sits at the bottom and the list does not jump when it grows.
 * Items fade in and slide into place (`animateItem`), the reading column stays 640dp wide.
 */
@Composable
private fun FeedColumn(
    messages: List<ChatMessage>,
    actions: FeedActions,
    preparing: Boolean,
    inlineError: (@Composable () -> Unit)?,
    listState: LazyListState,
) {
    LazyColumn(
        modifier = Modifier.contentColumn().fillMaxHeight().testTag(ChatTestTags.MESSAGE_LIST),
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = screenMargin(), vertical = AppTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
    ) {
        // The feed is reversed: the first item is drawn last, i.e. after the newest message.
        if (preparing) item(key = PREPARING_KEY, contentType = PREPARING_KEY) { PreparingIndicator(Modifier.animateItem()) }
        inlineError?.let { error ->
            item(key = INLINE_ERROR_KEY, contentType = INLINE_ERROR_KEY) { Box(Modifier.animateItem()) { error() } }
        }
        items(
            items = messages.asReversed(),
            key = ChatMessage::id,
            contentType = ChatMessage::role,
        ) { message ->
            Message(message, actions, Modifier.animateItem())
        }
    }
}

private val AT_BOTTOM_TOLERANCE = 4.dp

@Composable
private fun Message(
    message: ChatMessage,
    actions: FeedActions,
    modifier: Modifier = Modifier,
) {
    val toolCall = message.toolCall
    when {
        toolCall != null -> {
            ToolCallCard(
                state = toolCall.status,
                description = toolCall.description,
                arguments = toolCall.arguments,
                expanded = toolCall.expanded,
                onExpandToggle = { actions.onEvent(ChatUiEvent.ToggleToolCallDetails(message.id)) },
                modifier = modifier,
                fileHits = toolCall.fileHits,
                onOpenFile = actions.onOpenFile,
            )
        }

        message.role == ChatRole.USER -> {
            UserMessage(message.text, modifier)
        }

        else -> {
            AssistantMessage(message.text, modifier, actions.onOpenLink)
        }
    }
}

private const val INLINE_ERROR_KEY = "inline_error"
private const val PREPARING_KEY = "preparing"
