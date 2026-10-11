package com.example.phoneagent.data.auth

import com.example.phoneagent.domain.auth.ApiKeyValidationGateway
import com.example.phoneagent.domain.auth.ApiKeyValidationResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiKeyAuthTest {
    @Test
    fun `blank API key is rejected without a network request`() =
        runTest {
            val gateway = FakeValidationGateway(ApiKeyValidationResult.Valid)
            val auth = ApiKeyAuth("   ", gateway)

            assertEquals(AuthResult.Failure(AuthFailure.InvalidApiKey), auth.signIn())
            assertEquals(0, gateway.calls)
        }

    @Test
    fun `valid API key is returned as a success, ready for SessionManager to persist`() =
        runTest {
            val auth = ApiKeyAuth("api-key", FakeValidationGateway(ApiKeyValidationResult.Valid))

            assertEquals(AuthResult.Success(tokens("api-key")), auth.signIn())
        }

    @Test
    fun `rejected API key is a failure`() =
        runTest {
            val auth = ApiKeyAuth("api-key", FakeValidationGateway(ApiKeyValidationResult.Rejected))

            assertEquals(AuthResult.Failure(AuthFailure.InvalidApiKey), auth.signIn())
        }

    @Test
    fun `network validation failure is returned as data, not stored`() =
        runTest {
            val auth = ApiKeyAuth("api-key", FakeValidationGateway(ApiKeyValidationResult.NetworkFailure))

            assertEquals(AuthResult.Failure(AuthFailure.Network(null)), auth.signIn())
        }

    private fun tokens(key: String): AuthTokens =
        AuthTokens(
            accessToken = key,
            idToken = null,
            refreshToken = null,
            accessTokenExpiresAtEpochSeconds = null,
            earliestRefreshAtEpochSeconds = null,
            method = AuthMethod.API_KEY,
        )

    private class FakeValidationGateway(
        private val result: ApiKeyValidationResult,
    ) : ApiKeyValidationGateway {
        var calls: Int = 0

        override suspend fun validate(key: String): ApiKeyValidationResult {
            calls += 1
            return result
        }
    }
}
