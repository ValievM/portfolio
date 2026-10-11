package com.example.phoneagent.data.llm

import com.example.phoneagent.domain.auth.CredentialsProvider
import com.example.phoneagent.domain.auth.CredentialsResult
import com.example.phoneagent.domain.model.ModelCatalog
import com.example.phoneagent.domain.model.ModelCatalogResult
import com.example.phoneagent.domain.platform.DefaultDispatcherProvider
import com.example.phoneagent.domain.platform.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

public class OkHttpModelCatalog(
    private val profile: BackendProfile,
    private val credentialsProvider: CredentialsProvider,
    private val clientVersion: String,
    private val client: OkHttpClient = OkHttpClient(),
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
) : ModelCatalog {
    override suspend fun load(): ModelCatalogResult =
        try {
            val credentials = credentialsProvider.current()
            if (credentials !is CredentialsResult.Ready) {
                ModelCatalogResult.Unavailable
            } else {
                withContext(dispatchers.io) {
                    val url =
                        profile.baseUrl
                            .toHttpUrl()
                            .newBuilder()
                            .addPathSegment(MODELS_PATH)
                            .addQueryParameter(CLIENT_VERSION_PARAMETER, clientVersion)
                            .build()
                    val request =
                        Request
                            .Builder()
                            .url(url)
                            .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX${credentials.credentials.accessToken}")
                            .apply {
                                credentials.credentials.accountId?.let { header(ACCOUNT_ID_HEADER, it) }
                            }.get()
                            .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@withContext ModelCatalogResult.Unavailable
                        val body = response.body.string()
                        parseModels(body)
                    }
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            ModelCatalogResult.Unavailable
        }

    private fun parseModels(body: String): ModelCatalogResult {
        val root = Json.parseToJsonElement(body).jsonObject
        val models = (root[MODELS_FIELD] ?: root[DATA_FIELD])?.jsonArray ?: return ModelCatalogResult.Unavailable
        val slugs =
            models
                .mapNotNull { model ->
                    model.catalogId()
                }.filter(String::isNotBlank)
        return ModelCatalogResult.Available(slugs)
    }

    private fun JsonElement.catalogId(): String? =
        (this as? JsonObject)
            ?.let { fields -> fields[SLUG_FIELD] ?: fields[ID_FIELD] }
            ?.let { it as? JsonPrimitive }
            ?.takeIf { it.isString }
            ?.content
            ?.takeIf(String::isNotBlank)

    private companion object {
        private const val MODELS_PATH = "models"
        private const val CLIENT_VERSION_PARAMETER = "client_version"
        private const val MODELS_FIELD = "models"
        private const val DATA_FIELD = "data"
        private const val SLUG_FIELD = "slug"
        private const val ID_FIELD = "id"
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val ACCOUNT_ID_HEADER = "ChatGPT-Account-ID"
        private const val BEARER_PREFIX = "Bearer "
    }
}
