package com.example.phoneagent.domain.platform

import com.example.phoneagent.domain.conversation.ItemId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.util.UUID

public fun interface Clock {
    public fun nowEpochMillis(): Long
}

public interface DispatcherProvider {
    public val main: CoroutineDispatcher
    public val io: CoroutineDispatcher
    public val default: CoroutineDispatcher
}

/**
 * Production dispatchers. `main` resolves lazily: JVM unit tests never touch it, and resolving it
 * eagerly would fail where no Main dispatcher engine is on the classpath.
 */
public val DefaultDispatcherProvider: DispatcherProvider =
    object : DispatcherProvider {
        override val main: CoroutineDispatcher by lazy { Dispatchers.Main }
        override val io: CoroutineDispatcher by lazy { Dispatchers.IO }
        override val default: CoroutineDispatcher by lazy { Dispatchers.Default }
    }

/** Ids for new [com.example.phoneagent.domain.conversation.HistoryItem]s; a port so tests get deterministic ids. */
public fun interface IdGenerator {
    public fun newItemId(): ItemId
}

public val RandomIdGenerator: IdGenerator = IdGenerator { ItemId(UUID.randomUUID().toString()) }

public enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

public interface AppLogger {
    public fun log(
        level: LogLevel,
        tag: String,
        message: String,
        fields: Map<String, String> = emptyMap(),
        throwable: Throwable? = null,
    )
}

public fun AppLogger.debug(
    tag: String,
    message: String,
    fields: Map<String, String> = emptyMap(),
): Unit = log(LogLevel.DEBUG, tag, message, fields)

public fun AppLogger.info(
    tag: String,
    message: String,
    fields: Map<String, String> = emptyMap(),
): Unit = log(LogLevel.INFO, tag, message, fields)

public fun AppLogger.warn(
    tag: String,
    message: String,
    fields: Map<String, String> = emptyMap(),
    throwable: Throwable? = null,
): Unit = log(LogLevel.WARN, tag, message, fields, throwable)

public fun AppLogger.error(
    tag: String,
    message: String,
    fields: Map<String, String> = emptyMap(),
    throwable: Throwable? = null,
): Unit = log(LogLevel.ERROR, tag, message, fields, throwable)

/** One id per logical operation (e.g. a sign-in attempt), so its scattered log lines can be tied together. */
public fun newCorrelationId(): String = UUID.randomUUID().toString()
