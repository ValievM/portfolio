package com.example.phoneagent.data.auth

import com.example.phoneagent.domain.platform.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public sealed interface TokenRefreshOutcome {
    public data class Ready(
        public val tokens: AuthTokens,
    ) : TokenRefreshOutcome

    public data object SignedOut : TokenRefreshOutcome

    public data object NeedsSignIn : TokenRefreshOutcome

    public data class Failed(
        public val failure: AuthFailure,
    ) : TokenRefreshOutcome
}

public class TokenRefresher(
    private val tokenStore: TokenStore,
    private val gateway: TokenRefreshGateway,
    private val clock: Clock,
    private val refreshWindowSeconds: Long = 30L,
    private val mutex: Mutex = Mutex(),
) {
    // Guards every write to the token store and the generation counter. It is held only for the
    // write itself, never across a network call, so sign-in/sign-out are not blocked by a refresh.
    private val writeLock: Mutex = Mutex()
    private var generation: Long = 0L

    public suspend fun current(forceRefresh: Boolean = false): TokenRefreshOutcome =
        mutex.withLock {
            val (startGeneration, stored) = writeLock.withLock { generation to tokenStore.read() }
            val tokens = stored ?: return@withLock TokenRefreshOutcome.SignedOut
            if (!shouldRefresh(tokens, forceRefresh)) return@withLock TokenRefreshOutcome.Ready(tokens)
            val refreshToken = tokens.refreshToken ?: return@withLock TokenRefreshOutcome.NeedsSignIn

            when (val result = gateway.refresh(refreshToken)) {
                is RefreshResult.Success -> {
                    // The gateway doesn't know which AuthProvider originally signed in, so it
                    // cannot set the right method; preserve it from the tokens we just read.
                    val refreshed = result.tokens.copy(method = tokens.method)
                    if (storeIfSessionUnchanged(startGeneration) { tokenStore.write(refreshed) }) {
                        TokenRefreshOutcome.Ready(refreshed)
                    } else {
                        TokenRefreshOutcome.SignedOut
                    }
                }

                is RefreshResult.Failure -> {
                    if (result.failure != AuthFailure.InvalidGrant) return@withLock TokenRefreshOutcome.Failed(result.failure)
                    if (storeIfSessionUnchanged(startGeneration) { tokenStore.clear() }) {
                        TokenRefreshOutcome.NeedsSignIn
                    } else {
                        TokenRefreshOutcome.SignedOut
                    }
                }
            }
        }

    /**
     * Replaces the stored tokens (sign-in) or removes them (sign-out, [tokens] = null) and cancels
     * the effect of any refresh already in flight: it started from the previous session, so its
     * result must not write tokens back after a sign-out or over a newer sign-in.
     */
    public suspend fun replaceTokens(tokens: AuthTokens?) {
        writeLock.withLock {
            generation++
            if (tokens == null) tokenStore.clear() else tokenStore.write(tokens)
        }
    }

    private suspend fun storeIfSessionUnchanged(
        startGeneration: Long,
        write: suspend () -> Unit,
    ): Boolean = writeLock.withLock { (generation == startGeneration).also { unchanged -> if (unchanged) write() } }

    private fun shouldRefresh(
        tokens: AuthTokens,
        forceRefresh: Boolean,
    ): Boolean =
        forceRefresh || tokens.accessTokenExpiresAtEpochSeconds?.let { expiresAt ->
            val now = clock.nowEpochMillis() / MILLISECONDS_PER_SECOND
            when {
                now >= expiresAt -> true
                expiresAt - now > refreshWindowSeconds -> false
                else -> tokens.earliestRefreshAtEpochSeconds?.let { now >= it } ?: true
            }
        } ?: false

    private companion object {
        private const val MILLISECONDS_PER_SECOND: Long = 1_000L
    }
}
