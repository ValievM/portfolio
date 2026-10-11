package com.example.phoneagent.data.llm

import com.example.phoneagent.domain.auth.ApiKeyValidationGateway
import com.example.phoneagent.domain.auth.ApiKeyValidationResult
import com.example.phoneagent.domain.platform.DefaultDispatcherProvider
import com.example.phoneagent.domain.platform.DispatcherProvider
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

public class OkHttpApiKeyValidationGateway(
    private val profile: BackendProfile,
    private val client: OkHttpClient = OkHttpClient(),
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
) : ApiKeyValidationGateway {
    override suspend fun validate(key: String): ApiKeyValidationResult =
        withContext(dispatchers.io) {
            try {
                val url =
                    profile.baseUrl
                        .toHttpUrl()
                        .newBuilder()
                        .addPathSegment(MODELS_PATH)
                        .build()
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$key")
                        .get()
                        .build()
                client.newCall(request).execute().use { response ->
                    when (response.code) {
                        SUCCESS -> ApiKeyValidationResult.Valid
                        UNAUTHORIZED, FORBIDDEN -> ApiKeyValidationResult.Rejected
                        else -> ApiKeyValidationResult.NetworkFailure
                    }
                }
            } catch (_: IOException) {
                ApiKeyValidationResult.NetworkFailure
            } catch (_: IllegalArgumentException) {
                ApiKeyValidationResult.NetworkFailure
            }
        }

    private companion object {
        private const val MODELS_PATH: String = "models"
        private const val AUTHORIZATION_HEADER: String = "Authorization"
        private const val BEARER_PREFIX: String = "Bearer "
        private const val SUCCESS: Int = 200
        private const val UNAUTHORIZED: Int = 401
        private const val FORBIDDEN: Int = 403
    }
}
