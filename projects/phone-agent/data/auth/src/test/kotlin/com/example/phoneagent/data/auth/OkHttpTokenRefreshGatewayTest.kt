package com.example.phoneagent.data.auth

import com.example.phoneagent.domain.fakes.FakeAppLogger
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.net.UnknownHostException

class OkHttpTokenRefreshGatewayTest {
    @Test
    fun `exchange sends authorization code form and returns tokens`() =
        runTest {
            serverWith(tokensResponse()) { server ->
                val result = gateway(server).exchangeAuthorizationCode(EXCHANGE)
                val request = server.takeRequest()

                assertEquals(AuthResult.Success(tokens()), result)
                assertEquals("POST", request.method)
                assertEquals("/oauth/token", request.url.encodedPath)
                assertEquals(
                    mapOf(
                        "grant_type" to "authorization_code",
                        "client_id" to "client",
                        "code" to "code",
                        "redirect_uri" to "http://localhost:1455/auth/callback",
                        "code_verifier" to "verifier",
                    ),
                    request.formFields(),
                )
            }
        }

    @Test
    fun `exchange maps invalid grant`() =
        runTest {
            serverWith(invalidGrantResponse()) { server ->
                assertEquals(AuthResult.Failure(AuthFailure.InvalidGrant), gateway(server).exchangeAuthorizationCode(EXCHANGE))
            }
        }

    @Test
    fun `a non-success response other than invalid_grant logs the HTTP status and OAuth error instead of disappearing`() =
        runTest {
            val response =
                MockResponse
                    .Builder()
                    .code(400)
                    .body("""{"error":"invalid_client"}""")
                    .build()
            serverWith(response) { server ->
                val logger = FakeAppLogger()

                val result = gateway(server, logger).exchangeAuthorizationCode(EXCHANGE)

                assertEquals(AuthResult.Failure(AuthFailure.Network(null)), result)
                val logged = logger.events.single()
                assertEquals("400", logged.fields["httpStatus"])
                assertEquals("invalid_client", logged.fields["error"])
            }
        }

    @Test
    fun `exchange maps malformed JSON`() =
        runTest {
            serverWith(
                MockResponse
                    .Builder()
                    .code(200)
                    .body("not-json")
                    .build(),
            ) { server ->
                assertEquals(AuthResult.Failure(AuthFailure.MalformedToken), gateway(server).exchangeAuthorizationCode(EXCHANGE))
            }
        }

    @Test
    fun `a transient DNS failure is retried and does not surface to the caller`() =
        runTest {
            serverWith(tokensResponse()) { server ->
                var attempts = 0
                val flakyClient =
                    OkHttpClient
                        .Builder()
                        .addInterceptor { chain ->
                            attempts += 1
                            if (attempts == 1) throw UnknownHostException("simulated background DNS failure")
                            chain.proceed(chain.request())
                        }.build()

                val result = gateway(server, client = flakyClient).exchangeAuthorizationCode(EXCHANGE)

                assertEquals(AuthResult.Success(tokens()), result)
                assertEquals(2, attempts)
            }
        }

    @Test
    fun `a DNS failure that never recovers exhausts retries, logs each attempt, and returns Network`() =
        runTest {
            serverWith(tokensResponse()) { server ->
                var attempts = 0
                val alwaysFailingClient =
                    OkHttpClient
                        .Builder()
                        .addInterceptor {
                            attempts += 1
                            throw UnknownHostException("simulated persistent DNS failure")
                        }.build()
                val logger = FakeAppLogger()

                val result = gateway(server, logger, alwaysFailingClient).exchangeAuthorizationCode(EXCHANGE)

                assertEquals(AuthResult.Failure(AuthFailure.Network(null)), result)
                assertEquals(3, attempts)
                assertEquals(3, logger.events.size)
            }
        }

    @Test
    fun `exchange maps connection failure without exposing request data`() =
        runTest {
            val server = MockWebServer()
            server.start()
            val gateway = gateway(server)
            server.close()

            val result = gateway.exchangeAuthorizationCode(EXCHANGE)

            assertEquals(AuthResult.Failure(AuthFailure.Network(null)), result)
            assertFalse(result.toString().contains("verifier"))
        }

    @Test
    fun `refresh sends refresh token form and returns rotated tokens`() =
        runTest {
            serverWith(tokensResponse()) { server ->
                val result = gateway(server).refresh("old-refresh")
                val request = server.takeRequest()

                assertEquals(RefreshResult.Success(tokens()), result)
                assertEquals(
                    mapOf(
                        "grant_type" to "refresh_token",
                        "client_id" to "client",
                        "refresh_token" to "old-refresh",
                    ),
                    request.formFields(),
                )
            }
        }

    @Test
    fun `refresh maps invalid grant`() =
        runTest {
            serverWith(invalidGrantResponse()) { server ->
                assertEquals(RefreshResult.Failure(AuthFailure.InvalidGrant), gateway(server).refresh("refresh"))
            }
        }

    @Test
    fun `refresh maps malformed JSON`() =
        runTest {
            serverWith(
                MockResponse
                    .Builder()
                    .code(200)
                    .body("[]")
                    .build(),
            ) { server ->
                assertEquals(RefreshResult.Failure(AuthFailure.MalformedToken), gateway(server).refresh("refresh"))
            }
        }

    @Test
    fun `refresh maps connection failure and keeps tokens out of error`() =
        runTest {
            val server = MockWebServer()
            server.start()
            val gateway = gateway(server)
            server.close()

            val result = gateway.refresh("secret-refresh")

            assertEquals(RefreshResult.Failure(AuthFailure.Network(null)), result)
            assertTrue(result.toString().contains("Network"))
            assertFalse(result.toString().contains("secret-refresh"))
        }

    @Test
    fun `the token request dispatches through the port and the response comes without network`() =
        runTest {
            val dispatchers = RecordingDispatcherProvider()
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("ok")
                            .body(
                                (
                                    """{"access_token":"access","id_token":"id","refresh_token":"new-refresh",""" +
                                        """"expires_in":100,"earliest_refresh_at":50}"""
                                ).toResponseBody(jsonMediaType),
                            ).build()
                    }.build()
            val gateway =
                OkHttpTokenRefreshGateway(
                    tokenConfig,
                    FakeAppLogger(),
                    client,
                    nowEpochSeconds = { NOW_SECONDS },
                    retryDelay = {},
                    dispatchers = dispatchers,
                )

            assertEquals(AuthResult.Success(tokens()), gateway.exchangeAuthorizationCode(EXCHANGE))
            assertTrue(
                "the blocking section must go through dispatchers.io",
                dispatchers.ioDispatcher.dispatchCount.get() >= 1,
            )
        }

    private fun gateway(
        server: MockWebServer,
        logger: FakeAppLogger = FakeAppLogger(),
        client: OkHttpClient = OkHttpClient(),
    ): OkHttpTokenRefreshGateway =
        OkHttpTokenRefreshGateway(config(server), logger, client, nowEpochSeconds = { NOW_SECONDS }, retryDelay = {})

    private fun config(server: MockWebServer): AuthConfig =
        AuthConfig("issuer", "authorize", server.url("/oauth/token").toString(), "client", "redirect", "scope", emptyMap())

    private fun tokens(): AuthTokens = AuthTokens("access", "id", "new-refresh", NOW_SECONDS + 100L, 50L, AuthMethod.CHATGPT_BROWSER)

    private val jsonMediaType = "application/json".toMediaType()

    private val tokenConfig =
        AuthConfig("http://token.test", "authorize", "http://token.test/oauth/token", "client", "redirect", "scope", emptyMap())

    private fun tokensResponse(): MockResponse =
        MockResponse
            .Builder()
            .code(200)
            .body("""{"access_token":"access","id_token":"id","refresh_token":"new-refresh","expires_in":100,"earliest_refresh_at":50}""")
            .build()

    private fun invalidGrantResponse(): MockResponse =
        MockResponse
            .Builder()
            .code(400)
            .body("""{"error":"invalid_grant"}""")
            .build()

    private fun mockwebserver3.RecordedRequest.formFields(): Map<String, String> =
        requireNotNull(body).utf8().split('&').associate { field ->
            val (name, value) = field.split('=', limit = 2)
            URLDecoder.decode(name, Charsets.UTF_8.name()) to URLDecoder.decode(value, Charsets.UTF_8.name())
        }

    private inline fun serverWith(
        response: MockResponse,
        block: (MockWebServer) -> Unit,
    ) {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(response)
            block(server)
        }
    }

    private companion object {
        private const val NOW_SECONDS: Long = 1_000L
        private val EXCHANGE = AuthorizationCodeExchange("code", "verifier", "http://localhost:1455/auth/callback")
    }
}
