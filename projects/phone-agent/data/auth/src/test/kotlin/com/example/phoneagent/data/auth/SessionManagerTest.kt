package com.example.phoneagent.data.auth

import com.example.phoneagent.domain.auth.Credentials
import com.example.phoneagent.domain.auth.CredentialsResult
import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

class SessionManagerTest {
    @Test
    fun `successful sign in transitions through signing in and exposes JWT account identifier`() =
        runTest {
            val tokens = tokens(accountId = "account-1")
            val provider = FakeAuthProvider(AuthMethod.CHATGPT_BROWSER, AuthResult.Success(tokens))
            val manager = sessionManager(provider = provider)
            val states = mutableListOf<AuthState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { manager.state.collect(states::add) }

            manager.signIn(AuthMethod.CHATGPT_BROWSER)

            assertEquals(
                listOf(
                    AuthState.SignedOut,
                    AuthState.SigningIn(AuthMethod.CHATGPT_BROWSER),
                    AuthState.SignedIn(AuthMethod.CHATGPT_BROWSER, "account-1"),
                ),
                states,
            )
            assertEquals(CredentialsResult.Ready(credentials(tokens, "account-1")), manager.current())
        }

    @Test
    fun `provider failure becomes error and a second sign in is allowed`() =
        runTest {
            val provider =
                FakeAuthProvider(
                    AuthMethod.CHATGPT_BROWSER,
                    AuthResult.Failure(AuthFailure.Network("offline")),
                    AuthResult.Success(tokens(accountId = "account-2")),
                )
            val manager = sessionManager(provider = provider)

            manager.signIn(AuthMethod.CHATGPT_BROWSER)
            assertEquals(AuthState.Error(AuthMethod.CHATGPT_BROWSER, AuthFailure.Network("offline")), manager.state.value)

            manager.signIn(AuthMethod.CHATGPT_BROWSER)

            assertEquals(AuthState.SignedIn(AuthMethod.CHATGPT_BROWSER, "account-2"), manager.state.value)
        }

    @Test
    fun `invalid grant during forced refresh signs out instead of entering error`() =
        runTest {
            val store = InMemoryTokenStore()
            val provider = FakeAuthProvider(AuthMethod.CHATGPT_BROWSER, AuthResult.Success(tokens(accountId = "account-3")))
            val manager =
                sessionManager(
                    provider = provider,
                    store = store,
                    refreshResult = RefreshResult.Failure(AuthFailure.InvalidGrant),
                )
            manager.signIn(AuthMethod.CHATGPT_BROWSER)

            assertEquals(CredentialsResult.SignedOut, manager.refreshAfterUnauthorized())
            assertEquals(AuthState.SignedOut, manager.state.value)
            assertEquals(null, store.stored)
        }

    @Test
    fun `sign out clears token store and changes state to signed out`() =
        runTest {
            val store = InMemoryTokenStore()
            val manager = sessionManager(FakeAuthProvider(AuthMethod.API_KEY, AuthResult.Success(tokens())), store)
            manager.signIn(AuthMethod.API_KEY)

            manager.signOut()

            assertEquals(AuthState.SignedOut, manager.state.value)
            assertEquals(null, store.stored)
        }

    @Test
    fun `restore exposes a stored session without signing in again`() =
        runTest {
            val stored = tokens(accountId = "restored-account")
            val store = InMemoryTokenStore(stored)
            val provider = FakeAuthProvider(AuthMethod.CHATGPT_BROWSER, AuthResult.Success(stored))
            val manager = sessionManager(provider, store)

            manager.restore()

            assertEquals(AuthState.SignedIn(AuthMethod.CHATGPT_BROWSER, "restored-account"), manager.state.value)
        }

    @Test
    fun `restore reports the device code method instead of guessing browser from token shape`() =
        runTest {
            // Device code tokens have the same shape as browser tokens (both carry a refresh and
            // an id token), so a heuristic based on token shape cannot tell them apart.
            val stored = tokens(accountId = "device-account", method = AuthMethod.CHATGPT_DEVICE_CODE)
            val store = InMemoryTokenStore(stored)
            val manager = sessionManager(FakeAuthProvider(AuthMethod.CHATGPT_DEVICE_CODE, AuthResult.Success(stored)), store)

            manager.restore()

            assertEquals(AuthState.SignedIn(AuthMethod.CHATGPT_DEVICE_CODE, "device-account"), manager.state.value)
        }

    @Test
    fun `signing in with an unregistered method reports a configuration failure, not a network one`() =
        runTest {
            val manager = sessionManager(FakeAuthProvider(AuthMethod.CHATGPT_BROWSER, AuthResult.Success(tokens())))

            val result = manager.signIn(AuthMethod.CHATGPT_DEVICE_CODE)

            assertEquals(AuthResult.Failure(AuthFailure.ProviderUnavailable), result)
            assertEquals(AuthState.Error(AuthMethod.CHATGPT_DEVICE_CODE, AuthFailure.ProviderUnavailable), manager.state.value)
        }

    @Test
    fun `provider failure does not touch the token store`() =
        runTest {
            val store = InMemoryTokenStore()
            val provider = FakeAuthProvider(AuthMethod.CHATGPT_BROWSER, AuthResult.Failure(AuthFailure.Network("offline")))
            val manager = sessionManager(provider, store)

            manager.signIn(AuthMethod.CHATGPT_BROWSER)

            assertEquals(null, store.stored)
        }

    @Test
    fun `logs a sign-in failure without leaking the failure message`() =
        runTest {
            val logger = FakeAppLogger()
            val provider = FakeAuthProvider(AuthMethod.CHATGPT_BROWSER, AuthResult.Failure(AuthFailure.Network("secret-body")))
            val manager = sessionManager(provider, logger = logger)

            manager.signIn(AuthMethod.CHATGPT_BROWSER)

            val logged = logger.events.joinToString(" ") { it.message + " " + it.fields.values.joinToString(" ") }
            assertEquals(false, logged.contains("secret-body"))
            assertEquals(true, logged.contains("sign-in failed"))
        }

    @Test
    fun `a refresh that finishes after sign out does not bring the tokens back`() =
        runTest {
            val store = InMemoryTokenStore(tokens(accountId = "before"))
            val gate = CompletableDeferred<RefreshResult>()
            val manager =
                sessionManager(
                    provider = FakeAuthProvider(AuthMethod.CHATGPT_BROWSER),
                    store = store,
                    gateway = GatedRefreshGateway(gate),
                )

            val refresh = async { manager.refreshAfterUnauthorized() }
            advanceUntilIdle()
            manager.signOut()
            gate.complete(RefreshResult.Success(tokens(accountId = "late")))

            assertEquals(CredentialsResult.SignedOut, refresh.await())
            assertEquals(null, store.stored)
            assertEquals(AuthState.SignedOut, manager.state.value)
            assertEquals(CredentialsResult.SignedOut, manager.current())
        }

    @Test
    fun `a refresh that finishes after a new sign in does not overwrite the new tokens`() =
        runTest {
            val store = InMemoryTokenStore(tokens(accountId = "old"))
            val fresh = tokens(accountId = "fresh")
            val gate = CompletableDeferred<RefreshResult>()
            val manager =
                sessionManager(
                    provider = FakeAuthProvider(AuthMethod.CHATGPT_BROWSER, AuthResult.Success(fresh)),
                    store = store,
                    gateway = GatedRefreshGateway(gate),
                )

            val refresh = async { manager.refreshAfterUnauthorized() }
            advanceUntilIdle()
            manager.signIn(AuthMethod.CHATGPT_BROWSER)
            gate.complete(RefreshResult.Success(tokens(accountId = "stale")))
            refresh.await()

            assertEquals(fresh, store.stored)
        }

    @Test
    fun `a network failure while refreshing is temporary unavailability, not a sign out`() =
        runTest {
            val stored = tokens(accountId = "a")
            val store = InMemoryTokenStore(stored)
            val manager =
                sessionManager(
                    provider = FakeAuthProvider(AuthMethod.CHATGPT_BROWSER),
                    store = store,
                    refreshResult = RefreshResult.Failure(AuthFailure.Network("offline")),
                    clock = FakeClock(epochMillis = 400_000L),
                )

            assertEquals(CredentialsResult.Unavailable, manager.current())
            assertEquals(stored, store.stored)
        }

    @Test
    fun `a network failure during the forced refresh keeps the session and says the credentials are unavailable`() =
        runTest {
            val stored = tokens(accountId = "a")
            val store = InMemoryTokenStore()
            val manager =
                sessionManager(
                    provider = FakeAuthProvider(AuthMethod.CHATGPT_BROWSER, AuthResult.Success(stored)),
                    store = store,
                    refreshResult = RefreshResult.Failure(AuthFailure.Network("offline")),
                )
            manager.signIn(AuthMethod.CHATGPT_BROWSER)

            assertEquals(CredentialsResult.Unavailable, manager.refreshAfterUnauthorized())
            assertEquals(stored, store.stored)
            assertEquals(AuthState.Error(AuthMethod.CHATGPT_BROWSER, AuthFailure.Network("offline")), manager.state.value)
        }

    @Test
    fun `a successful forced refresh returns the fresh credentials`() =
        runTest {
            val fresh = tokens(accountId = "fresh-account")
            val manager =
                sessionManager(
                    provider = FakeAuthProvider(AuthMethod.CHATGPT_BROWSER, AuthResult.Success(tokens(accountId = "old"))),
                    refreshResult = RefreshResult.Success(fresh),
                )
            manager.signIn(AuthMethod.CHATGPT_BROWSER)

            val result = manager.refreshAfterUnauthorized()

            assertEquals(CredentialsResult.Ready(Credentials(fresh.accessToken, "fresh-account")), result)
        }

    private fun sessionManager(
        provider: AuthProvider,
        store: InMemoryTokenStore = InMemoryTokenStore(),
        refreshResult: RefreshResult = RefreshResult.Success(tokens()),
        logger: FakeAppLogger = FakeAppLogger(),
        gateway: TokenRefreshGateway = FakeRefreshGateway(refreshResult),
        clock: FakeClock = FakeClock(),
    ): SessionManager =
        SessionManager(
            providers = mapOf(provider.method to provider),
            tokenRefresher = TokenRefresher(store, gateway, clock),
            logger = logger,
        )

    private fun tokens(
        accountId: String? = null,
        method: AuthMethod = AuthMethod.CHATGPT_BROWSER,
    ): AuthTokens =
        AuthTokens(
            accessToken =
                jwt(
                    """{"exp":300,"https://api.openai.com/auth":{"chatgpt_account_id":${accountId?.let { "\"$it\"" } ?: "null"}}}""",
                ),
            idToken = null,
            refreshToken = "refresh",
            accessTokenExpiresAtEpochSeconds = 300L,
            earliestRefreshAtEpochSeconds = null,
            method = method,
        )

    private fun credentials(
        tokens: AuthTokens,
        accountId: String,
    ) = com.example.phoneagent.domain.auth
        .Credentials(tokens.accessToken, accountId)

    private fun jwt(payload: String): String =
        "header.${Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())}.signature"

    private class FakeAuthProvider(
        override val method: AuthMethod,
        vararg results: AuthResult,
    ) : AuthProvider {
        private val results = results.iterator()

        override suspend fun signIn(): AuthResult = results.next()
    }

    private class GatedRefreshGateway(
        private val gate: CompletableDeferred<RefreshResult>,
    ) : TokenRefreshGateway {
        override suspend fun refresh(refreshToken: String): RefreshResult = gate.await()
    }

    private class FakeRefreshGateway(
        private val result: RefreshResult,
    ) : TokenRefreshGateway {
        override suspend fun refresh(refreshToken: String): RefreshResult = result
    }

    private class InMemoryTokenStore(
        var stored: AuthTokens? = null,
    ) : TokenStore {
        override suspend fun read(): AuthTokens? = stored

        override suspend fun write(tokens: AuthTokens) {
            stored = tokens
        }

        override suspend fun clear() {
            stored = null
        }
    }
}
