package com.example.phoneagent.domain.contracts

import com.example.phoneagent.domain.auth.Credentials
import com.example.phoneagent.domain.auth.CredentialsProvider
import com.example.phoneagent.domain.auth.CredentialsResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What every [CredentialsProvider] must do, whoever implements it. The fake that other tests use and the real
 * session manager both run these tests, so the fake cannot quietly drift from the real thing. The case that
 * once slipped through is here on purpose: a temporary failure (no network) must never look like a lost session.
 */
public abstract class CredentialsProviderContract {
    /** A signed-in provider, plus what it must return now and after a forced refresh. */
    public class SignedIn(
        public val provider: CredentialsProvider,
        public val current: Credentials,
        public val refreshed: Credentials,
    )

    /** Signed in, and the token refresh works. */
    protected abstract fun signedIn(): SignedIn

    /** Nobody is signed in. */
    protected abstract fun noSession(): CredentialsProvider

    /** A session exists, but the token cannot be refreshed right now (no network). */
    protected abstract fun networkDown(): CredentialsProvider

    @Test
    public fun `a signed in session gives its credentials`() =
        runTest {
            val subject = signedIn()

            assertEquals(CredentialsResult.Ready(subject.current), subject.provider.current())
        }

    @Test
    public fun `a forced refresh gives fresh credentials`() =
        runTest {
            val subject = signedIn()

            assertEquals(CredentialsResult.Ready(subject.refreshed), subject.provider.refreshAfterUnauthorized())
        }

    @Test
    public fun `after a forced refresh the fresh credentials are the current ones`() =
        runTest {
            val subject = signedIn()
            subject.provider.refreshAfterUnauthorized()

            assertEquals(CredentialsResult.Ready(subject.refreshed), subject.provider.current())
        }

    @Test
    public fun `without a session both calls say signed out`() =
        runTest {
            val provider = noSession()

            assertEquals(CredentialsResult.SignedOut, provider.current())
            assertEquals(CredentialsResult.SignedOut, provider.refreshAfterUnauthorized())
        }

    @Test
    public fun `a network failure is unavailable for both calls, never signed out`() =
        runTest {
            val provider = networkDown()

            assertEquals(CredentialsResult.Unavailable, provider.current())
            assertEquals(CredentialsResult.Unavailable, provider.refreshAfterUnauthorized())
        }

    @Test
    public fun `a failed refresh does not end the session`() =
        runTest {
            val provider = networkDown()
            provider.refreshAfterUnauthorized()

            assertEquals(CredentialsResult.Unavailable, provider.current())
        }
}
