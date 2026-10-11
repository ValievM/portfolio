package com.example.phoneagent.domain.platform

/**
 * A log-safe stand-in for an exception: only the original type NAME is kept for diagnosis, while
 * the original message and cause chain are dropped — they routinely embed user input (file names,
 * request fragments) that no regex can reliably scrub. Stack frames are copied onto this instance
 * by [LogRedaction.sanitizedThrowable].
 */
public class SanitizedThrowable(
    public val originalType: String,
) : Throwable(originalType)
