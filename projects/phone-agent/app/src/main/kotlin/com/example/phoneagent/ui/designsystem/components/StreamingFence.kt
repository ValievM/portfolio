package com.example.phoneagent.ui.designsystem.components

private const val FENCE_MIN_LENGTH = 3
private const val MAX_FENCE_INDENT = 3

private class OpenFence(
    val marker: Char,
    val length: Int,
)

/**
 * While a reply streams in, a code fence is usually open: the renderer treats the last line of an
 * unclosed fence as the closing marker and drops it, so the line being typed would appear only
 * after the next one. For display only, this closes a fence that is still open at the end of
 * [text]. Fences follow CommonMark: up to 3 spaces of indent, at least 3 backticks or tildes, and
 * a closing fence of the same character that is at least as long as the opening one.
 */
internal fun closeUnclosedFence(text: String): String {
    var open: OpenFence? = null
    for (line in text.lines()) {
        val current = open
        open = if (current == null) openingFence(line) else current.takeUnless { closes(line, it) }
    }
    val unclosed = open ?: return text
    val closing = unclosed.marker.toString().repeat(unclosed.length)
    return if (text.endsWith("\n")) "$text$closing" else "$text\n$closing"
}

private fun openingFence(line: String): OpenFence? {
    val trimmed = line.trimStart(' ')
    val marker = trimmed.firstOrNull()?.takeIf { it == '`' || it == '~' }
    val length = if (marker == null) 0 else trimmed.takeWhile { it == marker }.length
    val infoAllowed = marker != '`' || '`' !in trimmed.drop(length)
    val fenced = marker != null && length >= FENCE_MIN_LENGTH && infoAllowed && line.length - trimmed.length <= MAX_FENCE_INDENT
    return if (fenced) OpenFence(requireNotNull(marker), length) else null
}

private fun closes(
    line: String,
    fence: OpenFence,
): Boolean {
    val trimmed = line.trimStart(' ')
    if (line.length - trimmed.length > MAX_FENCE_INDENT) return false
    val run = trimmed.takeWhile { it == fence.marker }.length
    return run >= fence.length && trimmed.drop(run).isBlank()
}
