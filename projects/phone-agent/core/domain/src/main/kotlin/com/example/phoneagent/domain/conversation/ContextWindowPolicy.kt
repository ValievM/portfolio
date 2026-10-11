package com.example.phoneagent.domain.conversation

/** Decides how much of the conversation to send on the next request (store=false: history is always sent in full). */
public fun interface ContextWindowPolicy {
    public fun apply(history: List<HistoryItem>): List<HistoryItem>
}

/**
 * Keeps whole turns in the request window: a turn is everything from a [HistoryItem.UserMessage] up
 * to (not including) the next one, so a paired [HistoryItem.ToolCallItem] and its
 * [HistoryItem.ToolOutput] can never end up on different sides of the boundary, and the window
 * always starts at a user message.
 *
 * The limit is soft: [maxItems] bounds how many of the OLDEST turns fit, while the newest turn is
 * always included whole even if it alone exceeds the limit. Items before the first user message
 * (none in normal operation) form their own group and count as a turn.
 */
public class LastNMessagesPolicy(
    private val maxItems: Int,
) : ContextWindowPolicy {
    init {
        require(maxItems > 0) { "maxItems must be positive, was $maxItems" }
    }

    override fun apply(history: List<HistoryItem>): List<HistoryItem> {
        if (history.isEmpty()) return history
        val groups = history.splitIntoTurns()
        val newest = groups.last()
        val selected = mutableListOf<List<HistoryItem>>(newest)
        var size = newest.size
        for (group in groups.subList(0, groups.size - 1).asReversed()) {
            if (size + group.size > maxItems) break
            selected.add(group)
            size += group.size
        }
        return selected.asReversed().flatten()
    }
}

/** Splits the history into whole turns by the positions of user messages. */
private fun List<HistoryItem>.splitIntoTurns(): List<List<HistoryItem>> =
    fold(mutableListOf<MutableList<HistoryItem>>()) { groups, item ->
        if (item is HistoryItem.UserMessage || groups.isEmpty()) groups.add(mutableListOf(item)) else groups.last().add(item)
        groups
    }
