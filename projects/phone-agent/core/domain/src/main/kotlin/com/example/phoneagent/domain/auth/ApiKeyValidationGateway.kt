package com.example.phoneagent.domain.auth

public sealed interface ApiKeyValidationResult {
    public data object Valid : ApiKeyValidationResult

    public data object Rejected : ApiKeyValidationResult

    public data object NetworkFailure : ApiKeyValidationResult
}

public interface ApiKeyValidationGateway {
    public suspend fun validate(key: String): ApiKeyValidationResult
}
