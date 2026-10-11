package com.example.phoneagent.tools.files

import com.example.phoneagent.domain.platform.Clock

/** Which hard limit stopped the walk early; only the ones actually observed are reported. */
internal enum class WalkLimit {
    DEPTH,
    NODES,
    TIME,
    RESULTS,
}

internal data class FoundFile(
    val name: String,
    /** Path relative to the searched root, '/'-separated, root name excluded. */
    val relativePath: String,
)

internal data class WalkResult(
    val matches: List<FoundFile>,
    val limitsHit: Set<WalkLimit>,
)

/** Hard traversal limits from the product spec for `find_files` (docs/stages/4-tools.md, 4.7). */
internal data class WalkLimits(
    /** Nodes deeper than this are not visited; the root itself is depth 0. */
    val maxDepth: Int = 6,
    val maxNodes: Int = 5_000,
    val timeoutMillis: Long = 5_000,
    val maxResults: Int = 20,
)

/**
 * Breadth-first search by document name inside the user-selected folder, enforcing the hard
 * [WalkLimits]: the walk never leaves the subtree rooted at [root], never reads file contents,
 * and stops early on the depth / visited-node / time / result-count limits, reporting which
 * limits were hit so callers can explain truncation. Names are matched as plain substrings
 * only: they are untrusted data and are never interpreted as instructions.
 */
internal class SafTreeWalker(
    private val clock: Clock,
) {
    fun search(
        root: DocumentNode,
        query: String,
        limits: WalkLimits = WalkLimits(),
    ): WalkResult {
        val state = WalkState(limits)
        state.start(clock)
        val queue = ArrayDeque<Frame>()
        queue.addLast(Frame(root, depth = 0, parentPath = ""))
        while (state.stopReason == null && queue.isNotEmpty() && !state.timeBudgetExceeded(clock)) {
            state.visit(queue.removeFirst(), query, queue)
        }
        return state.finish(
            timeExceededNow = state.timeBudgetExceeded(clock),
            hasPendingNodes = queue.isNotEmpty(),
        )
    }

    private data class Frame(
        val node: DocumentNode,
        val depth: Int,
        val parentPath: String,
    )

    /** Mutable accumulator of one walk; keeps [search] a plain loop over observable effects. */
    private class WalkState(
        private val limits: WalkLimits,
    ) {
        val matches = mutableListOf<FoundFile>()
        val limitsHit = mutableSetOf<WalkLimit>()
        private var visitedNodes = 0
        private var startedAt = 0L

        var stopReason: WalkLimit? = null
            private set

        fun start(clock: Clock) {
            startedAt = clock.nowEpochMillis()
        }

        fun timeBudgetExceeded(clock: Clock): Boolean = clock.nowEpochMillis() - startedAt > limits.timeoutMillis

        fun visit(
            frame: Frame,
            query: String,
            queue: ArrayDeque<Frame>,
        ) {
            val (node, depth, parentPath) = frame
            visitedNodes++
            val path = joinPath(parentPath, node.name)
            if (depth > 0 && node.name.contains(query, ignoreCase = true)) {
                matches += FoundFile(name = node.name, relativePath = path)
                if (matches.size >= limits.maxResults) stopReason = WalkLimit.RESULTS
            }
            if (stopReason == null) {
                expandChildren(frame, path, queue)
                if (visitedNodes >= limits.maxNodes && queue.isNotEmpty()) stopReason = WalkLimit.NODES
            }
        }

        fun finish(
            timeExceededNow: Boolean,
            hasPendingNodes: Boolean,
        ): WalkResult {
            stopReason?.let { limitsHit += it }
            if (stopReason == null && hasPendingNodes && timeExceededNow) limitsHit += WalkLimit.TIME
            return WalkResult(matches.toList(), limitsHit.toSet())
        }

        private fun expandChildren(
            frame: Frame,
            path: String,
            queue: ArrayDeque<Frame>,
        ) {
            if (frame.depth >= limits.maxDepth) {
                limitsHit += WalkLimit.DEPTH
            } else if (frame.node.isDirectory) {
                // Children of the root start a fresh path: the root itself is the search
                // scope, not a result path segment.
                val childParentPath = if (frame.depth == 0) "" else path
                queue += frame.node.children().map { Frame(it, frame.depth + 1, childParentPath) }
            }
        }

        private fun joinPath(
            parentPath: String,
            name: String,
        ): String = if (parentPath.isEmpty()) name else "$parentPath/$name"
    }
}
