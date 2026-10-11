package com.example.phoneagent.data.llm

import com.example.phoneagent.domain.auth.ApiKeyValidationResult
import com.example.phoneagent.domain.fakes.FakeDispatcherProvider
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

class OkHttpApiKeyValidationGatewayTest {
    @Test
    fun `200 validates key using models endpoint and bearer header`() =
        runTest {
            serverWith(200) { server ->
                val result = gateway(server).validate("test-key")
                val request = server.takeRequest()

                assertEquals(ApiKeyValidationResult.Valid, result)
                assertEquals("GET", request.method)
                assertEquals("/api/models", request.url.encodedPath)
                assertEquals("Bearer test-key", request.headers["Authorization"])
            }
        }

    @Test
    fun `401 rejects key`() =
        runTest {
            serverWith(401) { server ->
                assertEquals(ApiKeyValidationResult.Rejected, gateway(server).validate("key"))
            }
        }

    @Test
    fun `403 rejects key`() =
        runTest {
            serverWith(403) { server ->
                assertEquals(ApiKeyValidationResult.Rejected, gateway(server).validate("key"))
            }
        }

    @Test
    fun `unexpected status is a network failure`() =
        runTest {
            serverWith(500) { server ->
                assertEquals(ApiKeyValidationResult.NetworkFailure, gateway(server).validate("key"))
            }
        }

    @Test
    fun `connection failure is a network failure`() =
        runTest {
            val server = MockWebServer()
            server.start()
            val gateway = gateway(server)
            server.close()

            assertEquals(ApiKeyValidationResult.NetworkFailure, gateway.validate("key"))
        }

    @Test
    fun `the validation call runs on the dispatcher provided by the port`() =
        runTest {
            val seenThreads = mutableListOf<String>()
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        seenThreads += Thread.currentThread().name
                        chain.proceed(chain.request())
                    }.build()
            val io =
                Executors.newSingleThreadExecutor { r -> Thread(r, "apikey-test-io") }.asCoroutineDispatcher()
            try {
                serverWith(200) { server ->
                    val result =
                        OkHttpApiKeyValidationGateway(
                            BackendProfile(server.url("/api").toString().removeSuffix("/")),
                            client,
                            FakeDispatcherProvider(main = io, io = io, default = io),
                        ).validate("sk-test")

                    assertTrue(result is ApiKeyValidationResult.Valid)
                }
            } finally {
                io.close()
            }
            assertTrue(
                "expected the call on the provided io dispatcher, saw $seenThreads",
                seenThreads.isNotEmpty() && seenThreads.all { it.startsWith("apikey-test-io") },
            )
        }

    private fun gateway(server: MockWebServer): OkHttpApiKeyValidationGateway =
        OkHttpApiKeyValidationGateway(BackendProfile(server.url("/api").toString().removeSuffix("/")))

    private inline fun serverWith(
        code: Int,
        block: (MockWebServer) -> Unit,
    ) {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(code).build())
            block(server)
        }
    }
}
