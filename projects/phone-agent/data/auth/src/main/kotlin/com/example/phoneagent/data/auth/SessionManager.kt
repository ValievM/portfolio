package com.example.phoneagent.data.auth

import com.example.phoneagent.domain.auth.Credentials
import com.example.phoneagent.domain.auth.CredentialsProvider
import com.example.phoneagent.domain.auth.CredentialsResult
import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.info
import com.example.phoneagent.domain.platform.warn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Only failure kinds and AuthMethod names are ever logged here, never a token, account id, or
// anything from a raw HTTP response; AuthFailure's own toString() is already secret-free.
public class SessionManager(
    private val providers: Map<AuthMethod, AuthProvider>,
    private val tokenRefresher: TokenRefresher,
    private val logger: AppLogger,
) : CredentialsProvider {
    private val mutableState: MutableStateFlow<AuthState> = MutableStateFlow(AuthState.SignedOut)
    private val mutableReauthenticationFailure: MutableStateFlow<AuthFailure?> = MutableStateFlow(null)

    public val state: StateFlow<AuthState> = mutableState.asStateFlow()
    public val reauthenticationFailure: StateFlow<AuthFailure?> = mutableReauthenticationFailure.asStateFlow()

    public suspend fun restore() {
        when (val result = tokenRefresher.current()) {
            is TokenRefreshOutcome.Ready -> {
                mutableState.value = AuthState.SignedIn(result.tokens.method, accountId(result.tokens))
            }

            TokenRefreshOutcome.SignedOut -> {
                mutableState.value = AuthState.SignedOut
            }

            TokenRefreshOutcome.NeedsSignIn -> {
                logger.warn(TAG, "restore: session needs sign-in again (invalid_grant)")
                mutableReauthenticationFailure.value = AuthFailure.InvalidGrant
                mutableState.value = AuthState.SignedOut
            }

            is TokenRefreshOutcome.Failed -> {
                logger.warn(TAG, "restore failed", fields = mapOf("failure" to result.failure.toString()))
                mutableState.value = AuthState.Error(null, result.failure)
            }
        }
    }

    public suspend fun signIn(method: AuthMethod): AuthResult {
        mutableReauthenticationFailure.value = null
        val provider = providers[method]
        if (provider == null) {
            logger.warn(TAG, "sign-in requested for an unregistered method", fields = mapOf("method" to method.name))
            val result = AuthResult.Failure(AuthFailure.ProviderUnavailable)
            mutableState.value = AuthState.Error(method, result.failure)
            return result
        }

        mutableState.value = AuthState.SigningIn(method)
        return when (val result = provider.signIn()) {
            is AuthResult.Success -> {
                tokenRefresher.replaceTokens(result.tokens)
                mutableState.value = AuthState.SignedIn(method, accountId(result.tokens))
                logger.info(TAG, "signed in", fields = mapOf("method" to method.name))
                result
            }

            is AuthResult.Failure -> {
                logger.warn(
                    TAG,
                    "sign-in failed",
                    fields = mapOf("method" to method.name, "failure" to result.failure.toString()),
                )
                mutableState.value = AuthState.Error(method, result.failure)
                result
            }
        }
    }

    public suspend fun signOut() {
        tokenRefresher.replaceTokens(null)
        mutableReauthenticationFailure.value = null
        mutableState.value = AuthState.SignedOut
        logger.info(TAG, "signed out")
    }

    override suspend fun current(): CredentialsResult = credentials(forceRefresh = false)

    override suspend fun refreshAfterUnauthorized(): CredentialsResult = credentials(forceRefresh = true)

    private suspend fun credentials(forceRefresh: Boolean): CredentialsResult =
        when (val result = tokenRefresher.current(forceRefresh)) {
            is TokenRefreshOutcome.Ready -> {
                CredentialsResult.Ready(Credentials(result.tokens.accessToken, accountId(result.tokens)))
            }

            TokenRefreshOutcome.SignedOut,
            TokenRefreshOutcome.NeedsSignIn,
            -> {
                if (result is TokenRefreshOutcome.NeedsSignIn) {
                    logger.warn(TAG, "session needs sign-in again (invalid_grant)")
                }
                mutableReauthenticationFailure.value = AuthFailure.InvalidGrant
                mutableState.value = AuthState.SignedOut
                CredentialsResult.SignedOut
            }

            is TokenRefreshOutcome.Failed -> {
                logger.warn(TAG, "credentials unavailable", fields = mapOf("failure" to result.failure.toString()))
                mutableState.value = AuthState.Error(signedInMethod(), result.failure)
                CredentialsResult.Unavailable
            }
        }

    private fun accountId(tokens: AuthTokens): String? =
        (JwtClaimsExtractor.extract(tokens.accessToken, tokens.idToken) as? JwtClaimsResult.Success)
            ?.claims
            ?.chatGptAccountId

    private fun signedInMethod(): AuthMethod? = (mutableState.value as? AuthState.SignedIn)?.method

    private companion object {
        private const val TAG: String = "SessionManager"
    }
}
