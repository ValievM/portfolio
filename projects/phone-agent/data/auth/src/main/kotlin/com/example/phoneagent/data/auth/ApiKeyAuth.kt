package com.example.phoneagent.data.auth

import com.example.phoneagent.domain.auth.ApiKeyValidationGateway
import com.example.phoneagent.domain.auth.ApiKeyValidationResult

public class ApiKeyAuth(
    private val apiKey: String,
    private val validationGateway: ApiKeyValidationGateway,
) : AuthProvider {
    override val method: AuthMethod = AuthMethod.API_KEY

    // Does not touch TokenStore: SessionManager is the single owner of persistence for every
    // AuthProvider, writing tokens once after a successful AuthResult.Success.
    override suspend fun signIn(): AuthResult {
        if (apiKey.isBlank()) return AuthResult.Failure(AuthFailure.InvalidApiKey)

        return when (validationGateway.validate(apiKey)) {
            ApiKeyValidationResult.Valid -> {
                AuthResult.Success(
                    AuthTokens(
                        accessToken = apiKey,
                        idToken = null,
                        refreshToken = null,
                        accessTokenExpiresAtEpochSeconds = null,
                        earliestRefreshAtEpochSeconds = null,
                        method = AuthMethod.API_KEY,
                    ),
                )
            }

            ApiKeyValidationResult.Rejected -> {
                AuthResult.Failure(AuthFailure.InvalidApiKey)
            }

            ApiKeyValidationResult.NetworkFailure -> {
                AuthResult.Failure(AuthFailure.Network(null))
            }
        }
    }
}
