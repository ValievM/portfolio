package com.example.phoneagent.data.auth

import com.example.phoneagent.domain.fakes.FakeAppLogger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The blocking socket accept must run on the dispatcher supplied through the port. A receiver that
 * was never started has no server socket, so [LoopbackCallbackReceiver.awaitCallback] returns
 * `Failure(Network)` right away — from inside `withContext(dispatchers.io)`. The recording
 * dispatcher proves the block was dispatched through the port; with a plain `Dispatchers.IO` (the
 * mutation) it never fires. No real sockets, no real time.
 */
class LoopbackCallbackReceiverDispatcherTest {
    @Test
    fun `awaitCallback dispatches its work through the port without a started server socket`() =
        runTest {
            val dispatchers = RecordingDispatcherProvider()
            val receiver = LoopbackCallbackReceiver(FakeAppLogger(), dispatchers = dispatchers)

            val result = receiver.awaitCallback(timeoutMillis = 400L)

            assertEquals(CallbackResult.Failure(AuthFailure.Network(null)), result)
            assertTrue(
                "the blocking section must go through dispatchers.io",
                dispatchers.ioDispatcher.dispatchCount.get() >= 1,
            )
        }
}
