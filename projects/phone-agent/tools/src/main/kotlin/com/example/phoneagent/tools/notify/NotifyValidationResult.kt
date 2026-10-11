package com.example.phoneagent.tools.notify

public sealed interface NotifyValidationResult {
    public data object Valid : NotifyValidationResult

    public data class Invalid(
        public val error: NotifyValidationError,
    ) : NotifyValidationResult
}

public enum class NotifyValidationError {
    EMPTY_TITLE,
    EMPTY_MESSAGE,
    TITLE_TOO_LONG,
    MESSAGE_TOO_LONG,
}

public fun validateNotifyArgs(args: NotifyArgs): NotifyValidationResult =
    when {
        args.title.isBlank() -> NotifyValidationResult.Invalid(NotifyValidationError.EMPTY_TITLE)
        args.message.isBlank() -> NotifyValidationResult.Invalid(NotifyValidationError.EMPTY_MESSAGE)
        args.title.length > MAX_TITLE_LENGTH -> NotifyValidationResult.Invalid(NotifyValidationError.TITLE_TOO_LONG)
        args.message.length > MAX_MESSAGE_LENGTH -> NotifyValidationResult.Invalid(NotifyValidationError.MESSAGE_TOO_LONG)
        else -> NotifyValidationResult.Valid
    }

// These limits keep the notification readable in the system shade without truncating normal messages.
public const val MAX_TITLE_LENGTH: Int = 100
public const val MAX_MESSAGE_LENGTH: Int = 500
