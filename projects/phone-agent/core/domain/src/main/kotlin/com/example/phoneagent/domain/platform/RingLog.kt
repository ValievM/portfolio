package com.example.phoneagent.domain.platform

/**
 * Pure line-oriented ring buffer: keeps appending, and once past [maxBytes] drops whole lines from
 * the front. Kept separate from any file/Android I/O so the trimming rule is unit-testable without
 * a device; [com.example.phoneagent] app code owns writing the result to disk.
 */
public object RingLog {
    public fun append(
        existing: String,
        line: String,
        maxBytes: Int,
    ): String {
        val candidate = existing + line + "\n"
        if (candidate.toByteArray(Charsets.UTF_8).size <= maxBytes) return candidate

        // `result + "\n"` below adds one trailing byte the running byte count must leave room for.
        val budgetForContent = (maxBytes - 1).coerceAtLeast(0)
        val lines = ArrayDeque(candidate.trimEnd('\n').split('\n'))
        while (lines.size > 1 && joined(lines).toByteArray(Charsets.UTF_8).size > budgetForContent) {
            lines.removeFirst()
        }
        var result = joined(lines)
        while (result.isNotEmpty() && result.toByteArray(Charsets.UTF_8).size > budgetForContent) {
            result = result.dropLast(1)
        }
        return result + "\n"
    }

    private fun joined(lines: ArrayDeque<String>): String = lines.joinToString("\n")
}
