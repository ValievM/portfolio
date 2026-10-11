package com.example.phoneagent.domain.tools

/** The end result of a call after the whole pipeline (validation, approval, execution, truncation). */
public sealed interface ToolOutcome {
    public data class Success(
        public val text: String,
        public val content: ToolContent? = null,
    ) : ToolOutcome

    public data class Rejected(
        public val reason: String,
    ) : ToolOutcome

    public data class Failed(
        public val reason: String,
    ) : ToolOutcome
}

private const val REJECTED_LABEL = "Rejected: "
private const val FAILED_LABEL = "Failed: "

/**
 * The text a finished call leaves in the saved history: the model reads it on the next turn. A success is
 * its own text; a rejection and a failure carry a label so the model (and the chat) can tell them apart.
 */
public fun ToolOutcome.toOutputText(): String =
    when (this) {
        is ToolOutcome.Success -> text
        is ToolOutcome.Rejected -> REJECTED_LABEL + reason
        is ToolOutcome.Failed -> FAILED_LABEL + reason
    }

/**
 * Reads a saved result back, so the chat can draw the card of a call again after the app is reopened.
 * Only the label at the very start counts. A success text that itself begins with one of the labels would
 * read back as a failure: that can only change how an old card looks, never what the model is told.
 */
public fun toolOutcomeFromOutputText(text: String): ToolOutcome =
    when {
        text.startsWith(REJECTED_LABEL) -> ToolOutcome.Rejected(text.removePrefix(REJECTED_LABEL))
        text.startsWith(FAILED_LABEL) -> ToolOutcome.Failed(text.removePrefix(FAILED_LABEL))
        else -> ToolOutcome.Success(text)
    }
