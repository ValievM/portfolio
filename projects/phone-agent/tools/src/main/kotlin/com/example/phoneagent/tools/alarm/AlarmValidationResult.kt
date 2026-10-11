package com.example.phoneagent.tools.alarm

public sealed interface AlarmValidationResult {
    public data object Valid : AlarmValidationResult

    public data class Invalid(
        public val reason: String,
    ) : AlarmValidationResult
}

public fun validateAlarmArgs(args: AlarmArgs): AlarmValidationResult =
    when {
        args.hour !in MIN_HOUR..MAX_HOUR -> AlarmValidationResult.Invalid(AlarmCopy.VALIDATION_HOUR_RANGE)
        args.minute !in MINUTE_RANGE -> AlarmValidationResult.Invalid(AlarmCopy.VALIDATION_MINUTE_RANGE)
        else -> AlarmValidationResult.Valid
    }

private const val MIN_HOUR = 0
private const val MAX_HOUR = 23
private val MINUTE_RANGE: IntRange = 0..59
