package com.example.phoneagent.data.auth

import com.example.phoneagent.domain.platform.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import java.util.concurrent.atomic.AtomicInteger

/**
 * A dispatcher that counts every dispatch and runs the block immediately on the caller's thread.
 * Tests use it to prove a class sends its blocking work through [DispatcherProvider.io]: after the
 * call `dispatchCount` is positive, and with the provider replaced by a plain `Dispatchers.IO`
 * (the mutation) it stays zero.
 */
internal class RecordingDispatcher : CoroutineDispatcher() {
    val dispatchCount = AtomicInteger(0)

    override fun dispatch(
        context: kotlin.coroutines.CoroutineContext,
        block: Runnable,
    ) {
        dispatchCount.incrementAndGet()
        block.run()
    }

    override fun isDispatchNeeded(context: kotlin.coroutines.CoroutineContext): Boolean = true
}

/** Provider whose io is a [RecordingDispatcher]; main and default delegate to it too. */
internal class RecordingDispatcherProvider : DispatcherProvider {
    val ioDispatcher = RecordingDispatcher()

    override val main: CoroutineDispatcher = ioDispatcher
    override val io: CoroutineDispatcher = ioDispatcher
    override val default: CoroutineDispatcher = ioDispatcher
}
