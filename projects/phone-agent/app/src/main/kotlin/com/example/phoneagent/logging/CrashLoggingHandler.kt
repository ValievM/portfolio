package com.example.phoneagent.logging

import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.error

/**
 * Logs an uncaught exception before handing it to whatever handler was previously installed
 * (the OS default, which shows the crash dialog and lets debuggers/crash tools see it too).
 * The previous handler runs even if logging itself blows up: losing the crash report is worse
 * than losing the log line. Pure composition, no Android types, so it's unit-testable without a
 * device.
 */
internal fun crashLoggingHandler(
    logger: AppLogger,
    previous: Thread.UncaughtExceptionHandler?,
): Thread.UncaughtExceptionHandler =
    Thread.UncaughtExceptionHandler { thread, throwable ->
        try {
            logger.error("Crash", "Uncaught exception", fields = mapOf("thread" to thread.name), throwable = throwable)
        } catch (_: Exception) {
            // A broken logger must not mask the real crash: swallow, the OS still gets it below.
        } finally {
            previous?.uncaughtException(thread, throwable)
        }
    }
