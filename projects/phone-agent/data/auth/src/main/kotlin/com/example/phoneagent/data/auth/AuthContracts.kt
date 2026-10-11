package com.example.phoneagent.data.auth

public enum class AuthMethod {
    CHATGPT_BROWSER,
    API_KEY,
    CHATGPT_DEVICE_CODE,
}

public sealed interface AuthState {
    public data object SignedOut : AuthState

    public data class SigningIn(
        public val method: AuthMethod,
    ) : AuthState

    public data class SignedIn(
        public val method: AuthMethod,
        public val accountId: String?,
    ) : AuthState

    public data class Error(
        public val method: AuthMethod?,
        public val failure: AuthFailure,
    ) : AuthState
}

public sealed interface AuthFailure {
    public data object InvalidApiKey : AuthFailure

    public data object ProviderUnavailable : AuthFailure

    public data object InvalidGrant : AuthFailure

    public data object StateMismatch : AuthFailure

    public data object MissingAuthorizationCode : AuthFailure

    public data object TimedOut : AuthFailure

    public data object LoopbackPortUnavailable : AuthFailure

    public data object MalformedToken : AuthFailure

    public class Network(
        message: String?,
    ) : AuthFailure {
        public val message: String? = message?.takeUnless(String::isBlank)?.let { SAFE_MESSAGE }

        override fun equals(other: Any?): Boolean = other is Network && message == other.message

        override fun hashCode(): Int = message.hashCode()

        override fun toString(): String = "Network(message=$message)"

        private companion object {
            private const val SAFE_MESSAGE = "Network request failed"
        }
    }
}

public data class AuthTokens(
    public val accessToken: String,
    public val idToken: String?,
    public val refreshToken: String?,
    public val accessTokenExpiresAtEpochSeconds: Long?,
    public val earliestRefreshAtEpochSeconds: Long?,
    // The method that produced this session. OAuth gateways are shared by several AuthProvider
    // implementations and cannot know it themselves, so the owning AuthProvider stamps it on the
    // result it returns, and TokenRefresher preserves it across refreshes. Persisted so
    // SessionManager.restore() can report the real method instead of guessing from token shape.
    public val method: AuthMethod,
) {
    override fun toString(): String =
        "AuthTokens(accessToken=<redacted>, idToken=<redacted>, refreshToken=<redacted>, " +
            "accessTokenExpiresAtEpochSeconds=$accessTokenExpiresAtEpochSeconds, " +
            "earliestRefreshAtEpochSeconds=$earliestRefreshAtEpochSeconds, method=$method)"
}

public sealed interface AuthResult {
    public data class Success(
        public val tokens: AuthTokens,
    ) : AuthResult

    public data class Failure(
        public val failure: AuthFailure,
    ) : AuthResult
}

public interface AuthProvider {
    public val method: AuthMethod

    public suspend fun signIn(): AuthResult
}

public sealed interface RefreshResult {
    public data class Success(
        public val tokens: AuthTokens,
    ) : RefreshResult

    public data class Failure(
        public val failure: AuthFailure,
    ) : RefreshResult
}

public data class AuthorizationCodeExchange(
    public val code: String,
    public val codeVerifier: String,
    public val redirectUri: String,
)

public interface TokenRefreshGateway {
    public suspend fun exchangeAuthorizationCode(exchange: AuthorizationCodeExchange): AuthResult =
        AuthResult.Failure(AuthFailure.Network(null))

    public suspend fun refresh(refreshToken: String): RefreshResult
}

// Stamps the real sign-in method onto a gateway result. The OAuth gateway is shared by several
// AuthProvider implementations and cannot know which one is calling it.
internal fun AuthResult.withMethod(method: AuthMethod): AuthResult =
    when (this) {
        is AuthResult.Success -> AuthResult.Success(tokens.copy(method = method))
        is AuthResult.Failure -> this
    }

public interface TokenStore {
    public suspend fun read(): AuthTokens?

    public suspend fun write(tokens: AuthTokens)

    public suspend fun clear()
}
