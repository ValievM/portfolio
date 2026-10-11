package com.example.phoneagent.data.llm

import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.auth.Credentials
import com.example.phoneagent.domain.auth.CredentialsProvider
import com.example.phoneagent.domain.auth.CredentialsResult
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.conversation.ItemId
import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeDispatcherProvider
import com.example.phoneagent.domain.fakes.FakeSettingsRepository
import com.example.phoneagent.domain.model.ModelEvent
import com.example.phoneagent.domain.model.ModelRequest
import com.example.phoneagent.domain.storage.AppSettings
import com.example.phoneagent.domain.storage.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ResponsesGatewayTest {
    @Test
    fun `an unset model falls back to gpt-5-6-luna`() =
        runTest {
            withServer { server ->
                server.enqueue(sseResponse(sampleSse()))
                val settings = FakeSettingsRepository()

                gateway(server, settings = settings).stream(request()).toList()

                val requestBody = requireNotNull(server.takeRequest().body)
                val body = Json.parseToJsonElement(requestBody.utf8()).jsonObject
                assertEquals("gpt-5.6-luna", body.getValue("model").jsonPrimitive.content)
            }
        }

    @Test
    fun `a selected model is sent in the request`() =
        runTest {
            withServer { server ->
                server.enqueue(sseResponse(sampleSse()))
                val settings =
                    FakeSettingsRepository().apply {
                        this.settings = AppSettings(model = "gpt-5.6-terra")
                    }

                gateway(server, settings = settings).stream(request()).toList()

                val requestBody = requireNotNull(server.takeRequest().body)
                val body = Json.parseToJsonElement(requestBody.utf8()).jsonObject
                assertEquals("gpt-5.6-terra", body.getValue("model").jsonPrimitive.content)
            }
        }

    @Test
    fun `each request reads the current model setting`() =
        runTest {
            withServer { server ->
                server.enqueue(sseResponse(sampleSse()))
                server.enqueue(sseResponse(sampleSse()))
                val settings = FakeSettingsRepository()
                val gateway = gateway(server, settings = settings)

                gateway.stream(request()).toList()
                settings.settings = AppSettings(model = "gpt-5.6-terra")
                gateway.stream(request()).toList()

                val sentModels =
                    List(2) {
                        val requestBody = requireNotNull(server.takeRequest().body)
                        Json
                            .parseToJsonElement(requestBody.utf8())
                            .jsonObject
                            .getValue("model")
                            .jsonPrimitive.content
                    }
                assertEquals(listOf("gpt-5.6-luna", "gpt-5.6-terra"), sentModels)
            }
        }

    @Test
    fun `a signed-out credentials provider fails fast without any HTTP call`() =
        runTest {
            withServer { server ->
                val gateway = gateway(server, credentials = FakeCredentialsProvider(CredentialsResult.SignedOut))

                val events = gateway.stream(request()).toList()

                assertEquals(listOf(ModelEvent.Failed(AgentError.SessionExpired)), events)
                assertEquals(0, server.requestCount)
            }
        }

    @Test
    fun `temporarily unavailable credentials fail with Offline, not SessionExpired, without any HTTP call`() =
        runTest {
            withServer { server ->
                val gateway = gateway(server, credentials = FakeCredentialsProvider(CredentialsResult.Unavailable))

                val events = gateway.stream(request()).toList()

                assertEquals(listOf(ModelEvent.Failed(AgentError.Offline)), events)
                assertEquals(0, server.requestCount)
            }
        }

    @Test
    fun `a successful stream sends the bearer token and account id and emits parsed events in order`() =
        runTest {
            withServer { server ->
                server.enqueue(sseResponse(sampleSse()))
                val credentials = Credentials(accessToken = "token-1", accountId = "acct-1")
                val gateway = gateway(server, credentials = FakeCredentialsProvider(CredentialsResult.Ready(credentials)))

                val events = gateway.stream(request()).toList()

                val sent = server.takeRequest()
                assertEquals("Bearer token-1", sent.headers["Authorization"])
                assertEquals("acct-1", sent.headers["ChatGPT-Account-ID"])
                assertEquals(
                    listOf(ModelEvent.TextDelta("hi"), ModelEvent.Completed(null)),
                    events,
                )
            }
        }

    @Test
    fun `a 401 triggers exactly one refresh and retry, then succeeds`() =
        runTest {
            withServer { server ->
                server.enqueue(MockResponse.Builder().code(401).build())
                server.enqueue(sseResponse(sampleSse()))
                val credentialsProvider =
                    FakeCredentialsProvider(
                        current = CredentialsResult.Ready(Credentials("stale", null)),
                        refreshed = Credentials("fresh", null),
                    )
                val settings =
                    object : SettingsRepository {
                        var loads = 0

                        override suspend fun load(): AppSettings =
                            AppSettings(model = if (loads++ == 0) "gpt-5.6-luna" else "gpt-5.6-terra")

                        override suspend fun save(settings: AppSettings) = Unit
                    }

                val events = gateway(server, credentials = credentialsProvider, settings = settings).stream(request()).toList()
                val firstRequest = server.takeRequest()
                val secondRequest = server.takeRequest()

                assertEquals(1, credentialsProvider.refreshCalls)
                assertEquals("Bearer stale", firstRequest.headers["Authorization"])
                assertEquals("Bearer fresh", secondRequest.headers["Authorization"])
                assertEquals(1, settings.loads)
                val sentModels =
                    listOf(firstRequest, secondRequest).map { recordedRequest ->
                        val requestBody = requireNotNull(recordedRequest.body)
                        Json
                            .parseToJsonElement(requestBody.utf8())
                            .jsonObject
                            .getValue("model")
                            .jsonPrimitive.content
                    }
                assertEquals(listOf("gpt-5.6-luna", "gpt-5.6-luna"), sentModels)
                assertEquals(listOf(ModelEvent.TextDelta("hi"), ModelEvent.Completed(null)), events)
            }
        }

    @Test
    fun `a 401 that persists after the retry surfaces as SessionExpired`() =
        runTest {
            withServer { server ->
                server.enqueue(MockResponse.Builder().code(401).build())
                server.enqueue(MockResponse.Builder().code(401).build())
                val credentialsProvider =
                    FakeCredentialsProvider(
                        current = CredentialsResult.Ready(Credentials("stale", null)),
                        refreshed = Credentials("still-stale", null),
                    )

                val events = gateway(server, credentials = credentialsProvider).stream(request()).toList()

                assertEquals(1, credentialsProvider.refreshCalls)
                assertEquals(listOf(ModelEvent.Failed(AgentError.SessionExpired)), events)
            }
        }

    @Test
    fun `a 401 followed by a temporary refresh failure fails with Offline so the user can retry, not with SessionExpired`() =
        runTest {
            withServer { server ->
                server.enqueue(MockResponse.Builder().code(401).build())
                val credentialsProvider =
                    FakeCredentialsProvider(
                        current = CredentialsResult.Ready(Credentials("stale", null)),
                        refreshOutcome = CredentialsResult.Unavailable,
                    )

                val events = gateway(server, credentials = credentialsProvider).stream(request()).toList()

                assertEquals(1, credentialsProvider.refreshCalls)
                assertEquals(listOf(ModelEvent.Failed(AgentError.Offline)), events)
            }
        }

    @Test
    fun `a 401 followed by a lost session fails with SessionExpired`() =
        runTest {
            withServer { server ->
                server.enqueue(MockResponse.Builder().code(401).build())
                val credentialsProvider =
                    FakeCredentialsProvider(
                        current = CredentialsResult.Ready(Credentials("stale", null)),
                        refreshOutcome = CredentialsResult.SignedOut,
                    )

                val events = gateway(server, credentials = credentialsProvider).stream(request()).toList()

                assertEquals(listOf(ModelEvent.Failed(AgentError.SessionExpired)), events)
            }
        }

    @Test
    fun `a rejected request logs the error identifiers but never the response body`() =
        runTest {
            withServer { server ->
                server.enqueue(
                    MockResponse
                        .Builder()
                        .code(400)
                        .body(
                            """{"error":{"message":"Invalid text: 'SECRET chat text'","type":"invalid_request_error",""" +
                                """"param":"input[0].content[0].text","code":"invalid_value"}}""",
                        ).build(),
                )
                val logger = FakeAppLogger()

                gateway(server, logger = logger).stream(request()).toList()

                val logged = logger.events.single { it.message == "model request rejected" }
                assertEquals("400", logged.fields["status"])
                assertEquals("type=invalid_request_error code=invalid_value param=input[0].content[0].text", logged.fields["error"])
                assertTrue(logger.events.none { event -> "SECRET" in event.message || event.fields.values.any { "SECRET" in it } })
            }
        }

    @Test
    fun `403 maps to Forbidden`() =
        runTest {
            withServer { server ->
                server.enqueue(MockResponse.Builder().code(403).build())

                val events = gateway(server).stream(request()).toList()

                assertEquals(listOf(ModelEvent.Failed(AgentError.Forbidden)), events)
            }
        }

    @Test
    fun `429 maps to RateLimited with the Retry-After header`() =
        runTest {
            withServer { server ->
                server.enqueue(
                    MockResponse
                        .Builder()
                        .code(429)
                        .addHeader("Retry-After", "30")
                        .build(),
                )

                val events = gateway(server).stream(request()).toList()

                assertEquals(listOf(ModelEvent.Failed(AgentError.RateLimited(30))), events)
            }
        }

    @Test
    fun `a connection failure maps to Offline`() =
        runTest {
            val server = MockWebServer()
            server.start()
            val gateway = gateway(server)
            server.close()

            val events = gateway.stream(request()).toList()

            assertEquals(listOf(ModelEvent.Failed(AgentError.Offline)), events)
        }

    @Test
    fun `cancelling the collecting coroutine cancels the underlying call`() =
        runTest {
            withServer { server ->
                server.enqueue(
                    MockResponse
                        .Builder()
                        .body(sampleSse())
                        .bodyDelay(5, TimeUnit.SECONDS)
                        .build(),
                )
                val capturingFactory = CapturingCallFactory(cancellableClient())
                val gateway =
                    ResponsesGateway(
                        profile = profile(server),
                        credentialsProvider = FakeCredentialsProvider(),
                        settingsRepository = FakeSettingsRepository(),
                        logger = FakeAppLogger(),
                        client = capturingFactory.delegate,
                        callFactory = capturingFactory,
                    )

                // withTimeout on the test dispatcher measures virtual time, which never advances
                // while we're waiting on a real background thread; move to a real dispatcher.
                withContext(Dispatchers.Default) {
                    withTimeout(5_000) {
                        val collecting = async { gateway.stream(request()).toList() }
                        capturingFactory.callCreated.await()
                        collecting.cancel()
                        collecting.join()
                    }
                }

                assertTrue(capturingFactory.call.isCanceled())
            }
        }

    private fun request(): ModelRequest = ModelRequest(history = listOf(HistoryItem.UserMessage(ItemId("1"), "hi")))

    private fun profile(server: MockWebServer): BackendProfile =
        BackendProfile(baseUrl = server.url("/backend-api/codex").toString().removeSuffix("/"))

    // Long enough that only the explicit cancel() below can end the call, not a read timeout.
    private fun cancellableClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

    @Test
    fun `the request runs on the dispatcher provided by the port`() =
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
                Executors.newSingleThreadExecutor { r -> Thread(r, "gateway-test-io") }.asCoroutineDispatcher()
            try {
                withServer { server ->
                    server.enqueue(sseResponse(sampleSse()))
                    val gateway =
                        ResponsesGateway(
                            profile = profile(server),
                            credentialsProvider = FakeCredentialsProvider(),
                            settingsRepository = FakeSettingsRepository(),
                            logger = FakeAppLogger(),
                            client = client,
                            dispatchers = FakeDispatcherProvider(main = io, io = io, default = io),
                            newSessionId = { "session-1" },
                        )

                    gateway.stream(request()).first()
                }
            } finally {
                io.close()
            }
            assertTrue(
                "expected the request on the provided io dispatcher, saw $seenThreads",
                seenThreads.isNotEmpty() && seenThreads.all { it.startsWith("gateway-test-io") },
            )
        }

    private fun gateway(
        server: MockWebServer,
        credentials: CredentialsProvider = FakeCredentialsProvider(),
        settings: SettingsRepository = FakeSettingsRepository(),
        logger: FakeAppLogger = FakeAppLogger(),
    ): ResponsesGateway =
        ResponsesGateway(
            profile = profile(server),
            credentialsProvider = credentials,
            settingsRepository = settings,
            logger = logger,
            client = OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).build(),
            newSessionId = { "session-1" },
        )

    private fun sseResponse(text: String): MockResponse =
        MockResponse
            .Builder()
            .addHeader("Content-Type", "text/event-stream")
            .body(text)
            .build()

    private fun sampleSse(): String =
        "event: response.output_text.delta\ndata: {\"delta\":\"hi\"}\n\n" +
            "event: response.completed\ndata: {\"response\":{\"usage\":null}}\n\n"

    private inline fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }

    private class FakeCredentialsProvider(
        private val current: CredentialsResult = CredentialsResult.Ready(Credentials("token", null)),
        private val refreshed: Credentials? = null,
        private val refreshOutcome: CredentialsResult? = null,
    ) : CredentialsProvider {
        var refreshCalls: Int = 0

        override suspend fun current(): CredentialsResult = current

        override suspend fun refreshAfterUnauthorized(): CredentialsResult {
            refreshCalls += 1
            return refreshOutcome ?: refreshed?.let(CredentialsResult::Ready) ?: CredentialsResult.SignedOut
        }
    }

    private class CapturingCallFactory(
        val delegate: OkHttpClient,
    ) : Call.Factory {
        lateinit var call: Call
        val callCreated = CompletableDeferred<Unit>()

        override fun newCall(request: Request): Call {
            call = delegate.newCall(request)
            callCreated.complete(Unit)
            return call
        }
    }
}
