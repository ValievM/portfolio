package com.example.phoneagent.domain.fakes

import com.example.phoneagent.domain.auth.Credentials
import com.example.phoneagent.domain.auth.CredentialsProvider
import com.example.phoneagent.domain.auth.CredentialsResult
import com.example.phoneagent.domain.contracts.CredentialsProviderContract

/** The shared fake obeys the same contract as the real session manager (see SessionManagerContractTest). */
class FakeCredentialsProviderContractTest : CredentialsProviderContract() {
    override fun signedIn(): SignedIn {
        val current = Credentials("access-1", "account-1")
        val refreshed = Credentials("access-2", "account-2")
        val provider = FakeCredentialsProvider(CredentialsResult.Ready(current)).apply { refreshedCredentials = refreshed }
        return SignedIn(provider, current, refreshed)
    }

    override fun noSession(): CredentialsProvider = FakeCredentialsProvider(CredentialsResult.SignedOut)

    override fun networkDown(): CredentialsProvider = FakeCredentialsProvider(CredentialsResult.Unavailable)
}
