package com.example.phoneagent.data.auth

import com.example.phoneagent.domain.fakes.FakeClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class AuthPrimitivesTest {
    @Test
    fun `PKCE challenge matches RFC 7636 appendix B`() {
        val challenge = Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")

        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", challenge)
    }

    @Test
    fun `PKCE creates valid verifier and a new state for every request`() {
        val entropy = SequenceEntropy(listOf(1, 2, 3, 4))

        val first = Pkce.create(entropy)
        val second = Pkce.create(entropy)

        assertTrue(Pkce.isValidVerifier(first.verifier))
        assertEquals(43, first.verifier.length)
        assertTrue(Pkce.isValidVerifier(second.verifier))
        assertFalse(first.state == second.state)
    }

    @Test
    fun `JWT extracts account identifier from supported claim locations`() {
        val access = jwt("""{"exp":200}""")
        val cases =
            listOf(
                jwt("""{"chatgpt_account_id":"from-root"}""") to "from-root",
                jwt("""{"https://api.openai.com/auth":{"chatgpt_account_id":"from-id"}}""") to "from-id",
                jwt("""{"https://api.openai.com/auth":{"chatgpt_account_id":"from-access"}}""") to null,
            )

        cases.take(2).forEach { (idToken, expectedAccountId) ->
            val result = JwtClaimsExtractor.extract(access, idToken) as JwtClaimsResult.Success

            assertEquals(200L, result.claims.accessTokenExpiresAtEpochSeconds)
            assertEquals(expectedAccountId, result.claims.chatGptAccountId)
        }
        val accessWithAccount = jwt("""{"exp":200,"https://api.openai.com/auth":{"chatgpt_account_id":"from-access"}}""")
        val accessResult = JwtClaimsExtractor.extract(accessWithAccount, null) as JwtClaimsResult.Success
        assertEquals("from-access", accessResult.claims.chatGptAccountId)
    }

    @Test
    fun `JWT returns failure for malformed token`() {
        val result = JwtClaimsExtractor.extract("not-a-jwt", null)

        assertEquals(JwtClaimsResult.Failure(AuthFailure.MalformedToken), result)
    }

    @Test
    fun `JWT rejects malformed exp and ignores account claims with wrong type`() {
        listOf("{}", "[]", "\"200\"").forEach { invalidExp ->
            val result = JwtClaimsExtractor.extract(jwt("""{"exp":$invalidExp}"""), null)

            assertEquals(JwtClaimsResult.Failure(AuthFailure.MalformedToken), result)
        }
        listOf("{}", "[]", "42").forEach { invalidAccountId ->
            val result =
                JwtClaimsExtractor.extract(
                    jwt("""{"exp":200,"https://api.openai.com/auth":{"chatgpt_account_id":$invalidAccountId}}"""),
                    null,
                ) as JwtClaimsResult.Success

            assertNull(result.claims.chatGptAccountId)
        }
    }

    @Test
    fun `token string representation does not contain secrets`() {
        val tokens =
            AuthTokens(
                accessToken = "access-secret",
                idToken = "id-secret",
                refreshToken = "refresh-secret",
                accessTokenExpiresAtEpochSeconds = 200L,
                earliestRefreshAtEpochSeconds = 100L,
                method = AuthMethod.CHATGPT_BROWSER,
            )

        val rendered = tokens.toString()

        assertFalse(rendered.contains("access-secret"))
        assertFalse(rendered.contains("id-secret"))
        assertFalse(rendered.contains("refresh-secret"))
    }

    @Test
    fun `network failure removes response bodies and tokens from message`() {
        val failure =
            AuthFailure.Network(
                "HTTP 500: {\"access_token\":\"access-secret\"} Bearer refresh-secret",
            )

        assertEquals("Network request failed", failure.message)
        assertFalse(failure.message.orEmpty().contains("secret"))
    }

    @Test
    fun `proactive refresh respects earliest refresh hint but forced refresh ignores it`() =
        runTest {
            val tokens = tokens(expiresAt = 120L, earliestRefreshAt = 200L)
            val store = InMemoryTokenStore(tokens)
            val gateway = FakeRefreshGateway(RefreshResult.Success(tokens(expiresAt = 400L)))
            val refresher = TokenRefresher(store, gateway, FakeClock(epochMillis = 100_000L))

            assertEquals(TokenRefreshOutcome.Ready(tokens), refresher.current())
            assertEquals(0, gateway.calls)
            refresher.current(forceRefresh = true)

            assertEquals(1, gateway.calls)
        }

    @Test
    fun `expired token rotates refresh token before returning credentials`() =
        runTest {
            val oldTokens = tokens(expiresAt = 100L, refreshToken = "old-refresh")
            val newTokens = tokens(expiresAt = 400L, refreshToken = "new-refresh")
            val store = InMemoryTokenStore(oldTokens)
            val refresher =
                TokenRefresher(
                    tokenStore = store,
                    gateway = FakeRefreshGateway(RefreshResult.Success(newTokens)),
                    clock = FakeClock(epochMillis = 100_000L),
                )

            val outcome = refresher.current()

            assertEquals(TokenRefreshOutcome.Ready(newTokens), outcome)
            assertEquals(listOf(newTokens), store.writes)
            assertEquals("new-refresh", store.stored?.refreshToken)
        }

    @Test
    fun `refresh preserves the original sign-in method regardless of what the gateway returns`() =
        runTest {
            val oldTokens = tokens(expiresAt = 100L, method = AuthMethod.API_KEY)
            // The gateway is shared and stamps an arbitrary method; the refresher must not trust it.
            val gatewayTokens = tokens(expiresAt = 400L, method = AuthMethod.CHATGPT_BROWSER)
            val store = InMemoryTokenStore(oldTokens)
            val refresher =
                TokenRefresher(store, FakeRefreshGateway(RefreshResult.Success(gatewayTokens)), FakeClock(epochMillis = 100_000L))

            val outcome = refresher.current() as TokenRefreshOutcome.Ready

            assertEquals(AuthMethod.API_KEY, outcome.tokens.method)
            assertEquals(AuthMethod.API_KEY, store.stored?.method)
        }

    @Test
    fun `ten parallel requests perform one refresh`() =
        runTest {
            val oldTokens = tokens(expiresAt = 100L)
            val newTokens = tokens(expiresAt = 400L)
            val releaseRefresh = CompletableDeferred<Unit>()
            val gateway = FakeRefreshGateway(RefreshResult.Success(newTokens), releaseRefresh)
            val refresher = TokenRefresher(InMemoryTokenStore(oldTokens), gateway, FakeClock(epochMillis = 100_000L))

            val requests = List(10) { async(start = CoroutineStart.UNDISPATCHED) { refresher.current() } }
            assertEquals(1, gateway.calls)
            releaseRefresh.complete(Unit)
            requests.awaitAll()

            assertEquals(1, gateway.calls)
        }

    @Test
    fun `network refresh failure preserves session and invalid grant clears it`() =
        runTest {
            val validTokens = tokens(expiresAt = 100L)
            val networkStore = InMemoryTokenStore(validTokens)
            val networkRefresher =
                TokenRefresher(
                    networkStore,
                    FakeRefreshGateway(RefreshResult.Failure(AuthFailure.Network("offline"))),
                    FakeClock(epochMillis = 100_000L),
                )
            val invalidGrantStore = InMemoryTokenStore(validTokens)
            val invalidGrantRefresher =
                TokenRefresher(
                    invalidGrantStore,
                    FakeRefreshGateway(RefreshResult.Failure(AuthFailure.InvalidGrant)),
                    FakeClock(epochMillis = 100_000L),
                )

            assertEquals(TokenRefreshOutcome.Failed(AuthFailure.Network("offline")), networkRefresher.current())
            assertEquals(validTokens, networkStore.stored)
            assertEquals(TokenRefreshOutcome.NeedsSignIn, invalidGrantRefresher.current())
            assertEquals(null, invalidGrantStore.stored)
        }

    private fun tokens(
        expiresAt: Long,
        earliestRefreshAt: Long? = null,
        refreshToken: String = "refresh-token",
        method: AuthMethod = AuthMethod.CHATGPT_BROWSER,
    ): AuthTokens =
        AuthTokens(
            accessToken = "access-token",
            idToken = null,
            refreshToken = refreshToken,
            accessTokenExpiresAtEpochSeconds = expiresAt,
            earliestRefreshAtEpochSeconds = earliestRefreshAt,
            method = method,
        )

    private fun jwt(payload: String): String =
        "header.${Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())}.signature"

    private class SequenceEntropy(
        values: List<Int>,
    ) : AuthEntropy {
        private val values: Iterator<Int> = values.iterator()

        override fun nextBytes(size: Int): ByteArray {
            val value = values.next().toByte()
            return ByteArray(size) { value }
        }
    }

    private class InMemoryTokenStore(
        var stored: AuthTokens?,
    ) : TokenStore {
        val writes: MutableList<AuthTokens> = mutableListOf()

        override suspend fun read(): AuthTokens? = stored

        override suspend fun write(tokens: AuthTokens) {
            writes += tokens
            stored = tokens
        }

        override suspend fun clear() {
            stored = null
        }
    }

    private class FakeRefreshGateway(
        private val result: RefreshResult,
        private val waitForRelease: CompletableDeferred<Unit>? = null,
    ) : TokenRefreshGateway {
        var calls: Int = 0

        override suspend fun refresh(refreshToken: String): RefreshResult {
            calls += 1
            waitForRelease?.await()
            return result
        }
    }
}
