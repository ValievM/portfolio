package com.example.phoneagent.data.auth

import com.example.phoneagent.domain.auth.Credentials
import com.example.phoneagent.domain.auth.CredentialsProvider
import com.example.phoneagent.domain.contracts.CredentialsProviderContract
import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import java.util.Base64

/** The real session manager obeys the same contract as the fake (see FakeCredentialsProviderContractTest). */
class SessionManagerContractTest : CredentialsProviderContract() {
    override fun signedIn(): SignedIn {
        val stored = tokens("account-1")
        val fresh = tokens("account-2", accessTokenSuffix = "fresh")
        val manager = manager(Store(stored), RefreshResult.Success(fresh))
        return SignedIn(
            provider = manager,
            current = Credentials(stored.accessToken, "account-1"),
            refreshed = Credentials(fresh.accessToken, "account-2"),
        )
    }

    override fun noSession(): CredentialsProvider = manager(Store(null), RefreshResult.Success(tokens("unused")))

    // The stored token is already expired (the clock is far ahead), so it must be refreshed, and that fails on the network.
    override fun networkDown(): CredentialsProvider =
        manager(Store(tokens("account-1")), RefreshResult.Failure(AuthFailure.Network("offline")), FakeClock(epochMillis = 400_000L))

    private fun manager(
        store: Store,
        refreshResult: RefreshResult,
        clock: FakeClock = FakeClock(),
    ): SessionManager =
        SessionManager(
            providers = emptyMap(),
            tokenRefresher = TokenRefresher(store, Gateway(refreshResult), clock),
            logger = FakeAppLogger(),
        )

    private fun tokens(
        accountId: String,
        accessTokenSuffix: String = "",
    ): AuthTokens =
        AuthTokens(
            accessToken = jwt("""{"exp":300,"https://api.openai.com/auth":{"chatgpt_account_id":"$accountId"},"n":"$accessTokenSuffix"}"""),
            idToken = null,
            refreshToken = "refresh",
            accessTokenExpiresAtEpochSeconds = 300L,
            earliestRefreshAtEpochSeconds = null,
            method = AuthMethod.CHATGPT_BROWSER,
        )

    private fun jwt(payload: String): String =
        "header.${Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())}.signature"

    private class Gateway(
        private val result: RefreshResult,
    ) : TokenRefreshGateway {
        override suspend fun refresh(refreshToken: String): RefreshResult = result
    }

    private class Store(
        var stored: AuthTokens?,
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
