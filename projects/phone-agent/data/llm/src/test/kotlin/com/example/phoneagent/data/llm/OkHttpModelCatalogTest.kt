package com.example.phoneagent.data.llm

import com.example.phoneagent.domain.auth.Credentials
import com.example.phoneagent.domain.auth.CredentialsProvider
import com.example.phoneagent.domain.auth.CredentialsResult
import com.example.phoneagent.domain.fakes.FakeDispatcherProvider
import com.example.phoneagent.domain.model.ModelCatalogResult
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

class OkHttpModelCatalogTest {
    @Test
    fun `returns unavailable when models endpoint rejects the request`() =
        runTest {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(MockResponse.Builder().code(503).build())

                assertEquals(ModelCatalogResult.Unavailable, catalog(server).load())
            }
        }

    @Test
    fun `returns unavailable when models response is malformed`() =
        runTest {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(
                    MockResponse
                        .Builder()
                        .code(200)
                        .body("not-json")
                        .build(),
                )

                assertEquals(ModelCatalogResult.Unavailable, catalog(server).load())
            }
        }

    @Test
    fun `loads model ids from data response`() =
        runTest {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(
                    MockResponse
                        .Builder()
                        .code(200)
                        .body("""{"data":[{"id":"gpt-6-astra"}]}""")
                        .build(),
                )

                assertEquals(
                    ModelCatalogResult.Available(listOf("gpt-6-astra")),
                    catalog(server).load(),
                )
            }
        }

    @Test
    fun `loads model slugs from models response`() =
        runTest {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(
                    MockResponse
                        .Builder()
                        .code(200)
                        .body("""{"models":[{"slug":"gpt-5.6-luna"},{"slug":"gpt-5.6-terra"}]}""")
                        .build(),
                )

                val catalog = catalog(server)

                assertEquals(
                    ModelCatalogResult.Available(listOf("gpt-5.6-luna", "gpt-5.6-terra")),
                    catalog.load(),
                )
                val request = server.takeRequest()
                assertEquals("/api/models", request.url.encodedPath)
                assertEquals("test-version", request.url.queryParameter("client_version"))
                assertEquals("Bearer test-token", request.headers["Authorization"])
            }
        }

    private fun readyCredentials(): CredentialsProvider =
        object : CredentialsProvider {
            override suspend fun current(): CredentialsResult = CredentialsResult.Ready(Credentials("test-token", accountId = null))

            override suspend fun refreshAfterUnauthorized(): CredentialsResult = CredentialsResult.SignedOut
        }

    @Test
    fun `the network call runs on the dispatcher provided by the port`() =
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
                Executors.newSingleThreadExecutor { r -> Thread(r, "catalog-test-io") }.asCoroutineDispatcher()
            try {
                MockWebServer().use { server ->
                    server.start()
                    server.enqueue(
                        MockResponse
                            .Builder()
                            .code(200)
                            .body("""{"models":[{"slug":"gpt-5.6-luna"}]}""")
                            .build(),
                    )

                    val result =
                        OkHttpModelCatalog(
                            profile = BackendProfile(server.url("/api").toString().removeSuffix("/")),
                            credentialsProvider = readyCredentials(),
                            clientVersion = "test-version",
                            client = client,
                            dispatchers = FakeDispatcherProvider(main = io, io = io, default = io),
                        ).load()

                    assertTrue(result is ModelCatalogResult.Available)
                }
            } finally {
                io.close()
            }
            assertTrue(
                "expected the call on the provided io dispatcher, saw $seenThreads",
                seenThreads.isNotEmpty() && seenThreads.all { it.startsWith("catalog-test-io") },
            )
        }

    private fun catalog(server: MockWebServer): OkHttpModelCatalog =
        OkHttpModelCatalog(
            profile = BackendProfile(server.url("/api").toString().removeSuffix("/")),
            credentialsProvider = readyCredentials(),
            clientVersion = "test-version",
            client = OkHttpClient(),
        )
}
