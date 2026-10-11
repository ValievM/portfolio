package com.example.phoneagent.domain.auth

public data class Credentials(
    public val accessToken: String,
    public val accountId: String?,
)

public sealed interface CredentialsResult {
    public data class Ready(
        public val credentials: Credentials,
    ) : CredentialsResult

    public data object SignedOut : CredentialsResult

    /** Credentials cannot be produced right now (e.g. the token refresh hit a network error), but the session is not lost. */
    public data object Unavailable : CredentialsResult
}

public interface CredentialsProvider {
    public suspend fun current(): CredentialsResult

    /**
     * Forces a token refresh after the backend answered 401. [CredentialsResult.SignedOut] means the session is
     * gone (the user must sign in again); [CredentialsResult.Unavailable] means the refresh could not be done right
     * now (no network) and the session is kept, so the caller reports a temporary failure, not an expired session.
     */
    public suspend fun refreshAfterUnauthorized(): CredentialsResult
}
