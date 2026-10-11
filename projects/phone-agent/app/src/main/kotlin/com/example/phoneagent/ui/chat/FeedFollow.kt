package com.example.phoneagent.ui.chat

/** Who produced the message that just arrived in the feed. */
internal enum class NewMessageOrigin {
    User,
    Model,
}

internal enum class FeedScrollAction {
    ScrollToBottom,
    ShowNewMessagesChip,
}

/**
 * What the feed should do when a new message arrives (docs/design/layout-rules.md): follow the
 * conversation only if the reader is already at the bottom or has just sent the message
 * themselves; otherwise leave the scroll position alone and offer the "↓ к новым" chip.
 */
internal fun feedScrollActionForNewMessage(
    wasAtBottom: Boolean,
    origin: NewMessageOrigin,
): FeedScrollAction =
    if (wasAtBottom || origin == NewMessageOrigin.User) {
        FeedScrollAction.ScrollToBottom
    } else {
        FeedScrollAction.ShowNewMessagesChip
    }

/**
 * The feed is reversed (index 0 is the newest message, drawn at the bottom), so "at the bottom"
 * means the newest item is the first visible one, give or take a fling's residual offset.
 */
internal fun isAtBottom(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    tolerancePx: Int,
): Boolean = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset <= tolerancePx

internal fun isNewMessagesChipVisible(
    atBottom: Boolean,
    hasUnseenMessages: Boolean,
): Boolean = !atBottom && hasUnseenMessages
