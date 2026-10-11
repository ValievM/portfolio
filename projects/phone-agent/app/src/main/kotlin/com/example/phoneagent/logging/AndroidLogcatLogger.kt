package com.example.phoneagent.logging

import android.content.Context
import android.util.Log
import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.LogLevel
import com.example.phoneagent.domain.platform.LogRedaction
import com.example.phoneagent.domain.platform.RingLog
import com.example.phoneagent.domain.platform.SanitizedThrowable
import java.io.File
import java.io.IOException

/**
 * The one file in the app allowed to touch [android.util.Log] directly (see
 * ArchitectureTest.production code uses AppLogger instead of Android Log). Always bind this
 * behind [com.example.phoneagent.domain.platform.RedactingAppLogger], never as [AppLogger] itself.
 *
 * Defense in depth on top of that binding: whatever throwable reaches this sink is sanitized
 * again ([LogRedaction.sanitizedThrowable]) so its message and causes never reach Logcat or the
 * file even if a future caller forgets the decorator.
 *
 * Besides Logcat, it keeps a small rolling copy of recent log lines in a file under
 * noBackupFilesDir, so a crash or a support request has something to look at even if the device's
 * own Logcat buffer already scrolled past it. The trimming itself is [RingLog], tested without
 * Android. A failure to write the file is swallowed on purpose — Logcat already has the line, and
 * logging must never break the app.
 */
public class AndroidLogcatLogger
    internal constructor(
        private val file: File,
        private val maxFileBytes: Int = MAX_FILE_BYTES,
        private val logcat: (LogLevel, String, String, Throwable?) -> Unit = ::emitToLogcat,
    ) : AppLogger {
        private val lock = Any()

        public constructor(
            context: Context,
            maxFileBytes: Int = MAX_FILE_BYTES,
        ) : this(
            file = File(context.applicationContext.noBackupFilesDir, LOG_FILE_NAME),
            maxFileBytes = maxFileBytes,
        )

        override fun log(
            level: LogLevel,
            tag: String,
            message: String,
            fields: Map<String, String>,
            throwable: Throwable?,
        ) {
            // Fields must be folded into the Logcat message itself: Log.d/i/w/e take no structured
            // payload, so a field passed only to the ring-buffer file would be invisible in Logcat.
            val fullMessage = message + fieldsSuffix(fields)
            val safeThrowable = throwable?.let(LogRedaction::sanitizedThrowable)
            logcat(level, tag, fullMessage, safeThrowable)
            appendToFile(formatLine(level, tag, fullMessage, safeThrowable))
        }

        private fun fieldsSuffix(fields: Map<String, String>): String =
            if (fields.isEmpty()) "" else " " + fields.entries.joinToString(" ") { (key, value) -> "$key=$value" }

        private fun appendToFile(line: String) {
            try {
                synchronized(lock) {
                    val existing = runCatching { file.readText() }.getOrDefault("")
                    file.writeText(RingLog.append(existing, line, maxFileBytes))
                }
            } catch (_: IOException) {
                // A full disk or a broken path must not crash the app; Logcat already has the line.
            }
        }

        private fun formatLine(
            level: LogLevel,
            tag: String,
            fullMessage: String,
            throwable: Throwable?,
        ): String {
            // The throwable is already sanitized, so the file deliberately records only the
            // ORIGINAL type name — never a message, which is where untrusted input hides.
            val throwableText =
                when (throwable) {
                    null -> ""
                    is SanitizedThrowable -> " | ${throwable.originalType}"
                    else -> " | ${throwable::class.simpleName}"
                }
            return "${System.currentTimeMillis()} $level/$tag: $fullMessage$throwableText"
        }

        private companion object {
            private const val LOG_FILE_NAME: String = "app.log"
            private const val MAX_FILE_BYTES: Int = 64 * 1024
        }
    }

private fun emitToLogcat(
    level: LogLevel,
    tag: String,
    message: String,
    throwable: Throwable?,
) {
    when (level) {
        LogLevel.DEBUG -> Log.d(tag, message, throwable)
        LogLevel.INFO -> Log.i(tag, message, throwable)
        LogLevel.WARN -> Log.w(tag, message, throwable)
        LogLevel.ERROR -> Log.e(tag, message, throwable)
    }
}
