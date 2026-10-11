package com.example.phoneagent.domain.platform

/**
 * Decorates any [AppLogger] with [LogRedaction], so every real implementation (Logcat, a file, a
 * future crash reporter) gets it for free instead of redacting by hand. Bind this in front of the
 * platform-specific delegate; never bind the delegate directly as [AppLogger].
 */
public class RedactingAppLogger(
    private val delegate: AppLogger,
) : AppLogger {
    override fun log(
        level: LogLevel,
        tag: String,
        message: String,
        fields: Map<String, String>,
        throwable: Throwable?,
    ) {
        delegate.log(
            level = level,
            tag = tag,
            message = LogRedaction.redact(message),
            fields =
                fields.mapValues { (key, value) ->
                    if (LogRedaction.isSensitiveFieldName(key)) REDACTED_VALUE else LogRedaction.redact(value)
                },
            throwable = throwable?.let(::redacted),
        )
    }

    // Frames only carry class/method/line, never local values, so they're safe to keep; the
    // message and the cause chain are dropped entirely — exception text can embed raw user input.
    private fun redacted(throwable: Throwable): Throwable = LogRedaction.sanitizedThrowable(throwable)

    private companion object {
        private const val REDACTED_VALUE: String = "<redacted>"
    }
}
