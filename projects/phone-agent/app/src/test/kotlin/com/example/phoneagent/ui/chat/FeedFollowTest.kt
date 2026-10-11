package com.example.phoneagent.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedFollowTest {
    @Test
    fun `a model message while the user is at the bottom scrolls to the bottom`() {
        assertEquals(
            FeedScrollAction.ScrollToBottom,
            feedScrollActionForNewMessage(wasAtBottom = true, origin = NewMessageOrigin.Model),
        )
    }

    @Test
    fun `a model message while the user reads older messages only offers the chip`() {
        assertEquals(
            FeedScrollAction.ShowNewMessagesChip,
            feedScrollActionForNewMessage(wasAtBottom = false, origin = NewMessageOrigin.Model),
        )
    }

    @Test
    fun `the users own message always scrolls to the bottom even from older messages`() {
        assertEquals(
            FeedScrollAction.ScrollToBottom,
            feedScrollActionForNewMessage(wasAtBottom = false, origin = NewMessageOrigin.User),
        )
    }

    @Test
    fun `the users own message at the bottom scrolls to the bottom`() {
        assertEquals(
            FeedScrollAction.ScrollToBottom,
            feedScrollActionForNewMessage(wasAtBottom = true, origin = NewMessageOrigin.User),
        )
    }

    @Test
    fun `the newest item fully in view counts as being at the bottom`() {
        assertTrue(isAtBottom(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0, tolerancePx = 8))
    }

    @Test
    fun `a residual scroll offset within the tolerance still counts as the bottom`() {
        assertTrue(isAtBottom(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 8, tolerancePx = 8))
    }

    @Test
    fun `an offset just past the tolerance is not the bottom`() {
        assertFalse(isAtBottom(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 9, tolerancePx = 8))
    }

    @Test
    fun `any older first visible item is not the bottom whatever the offset`() {
        assertFalse(isAtBottom(firstVisibleItemIndex = 1, firstVisibleItemScrollOffset = 0, tolerancePx = 8))
    }

    @Test
    fun `the chip is shown only while away from the bottom with unseen messages`() {
        assertTrue(isNewMessagesChipVisible(atBottom = false, hasUnseenMessages = true))
        assertFalse(isNewMessagesChipVisible(atBottom = true, hasUnseenMessages = true))
        assertFalse(isNewMessagesChipVisible(atBottom = false, hasUnseenMessages = false))
    }
}
