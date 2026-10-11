package com.example.phoneagent.data.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.phoneagent.domain.fakes.FakeAppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.net.ServerSocket
import java.util.Base64
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LoopbackCallbackReceiverTest {
    @Test
    fun receivesCallbackFromLocalhost() = receivesCallbackFrom("localhost")

    @Test
    fun receivesCallbackFromIpv4Loopback() = receivesCallbackFrom("127.0.0.1")

    private fun receivesCallbackFrom(host: String) =
        runBlocking {
            val port = ServerSocket(0).use { it.localPort }
            val receiver = LoopbackCallbackReceiver(FakeAppLogger(), port)
            val code = UUID.randomUUID().toString()
            val state = UUID.randomUUID().toString()

            assertEquals(CallbackReceiverStartResult.Started, receiver.start())
            try {
                val callback = async(Dispatchers.IO) { receiver.awaitCallback(timeoutMillis = 5_000L) }
                val response = sendCallbackFromAdbShell(host, port, code, state)

                val received = callback.await()
                assertTrue(received is CallbackResult.Received)
                received as CallbackResult.Received
                assertTrue(received.code == code)
                assertTrue(received.state == state)
                assertTrue("Response has no CRLF header separator", response.contains("\r\n\r\n"))
                assertTrue(!response.contains("\\r\\n"))
            } finally {
                receiver.close()
            }
        }

    private fun sendCallbackFromAdbShell(
        host: String,
        port: Int,
        code: String,
        state: String,
    ): String {
        val request = "GET /auth/callback?code=$code&state=$state HTTP/1.1\\r\\nHost: $host\\r\\n\\r\\n"
        val script = "printf '$request' | toybox nc -w 5 $host $port; printf callback-sent"
        val encodedScript = Base64.getEncoder().encodeToString(script.toByteArray())
        val command = "sh -c echo${'$'}{IFS}$encodedScript|toybox${'$'}{IFS}base64${'$'}{IFS}-d|sh"
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        val result = output?.use { descriptor -> FileInputStream(descriptor.fileDescriptor).bufferedReader().readText() }
        assertEquals("callback-sent", result?.substringAfterLast("Authorization completed. You can return to the app.")?.trim())
        return result.orEmpty()
    }
}
