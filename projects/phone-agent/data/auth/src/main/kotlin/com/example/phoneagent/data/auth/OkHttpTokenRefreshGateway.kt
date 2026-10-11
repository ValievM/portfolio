package com.example.phoneagent.data.auth

import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.DefaultDispatcherProvider
import com.example.phoneagent.domain.platform.DispatcherProvider
import com.example.phoneagent.domain.platform.warn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

private const val TAG: String = "TokenRefreshGateway"

public class OkHttpTokenRefreshGateway(
    private val config: AuthConfig,
    private val logger: AppLogger,
    private val client: OkHttpClient = OkHttpClient(),
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / MILLIS_PER_SECOND },
    // Android can cut a backgrounded app's network access (Doze/App Standby/battery
    // restrictions), which is exactly the window this call runs in: the user is still looking
    // at the "you can close this" browser page when the loopback callback fires and this
    // request goes out. A DNS/connection failure there is often transient, not "no internet".
    private val retryDelaysMillis: List<Long> = listOf(FIRST_RETRY_DELAY_MILLIS, SECOND_RETRY_DELAY_MILLIS),
    private val retryDelay: suspend (Long) -> Unit = { millis -> delay(millis) },
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
) : TokenRefreshGateway {
    override suspend fun exchangeAuthorizationCode(exchange: AuthorizationCodeExchange): AuthResult =
        request(
            mapOf(
                "grant_type" to "authorization_code",
                "client_id" to config.clientId,
                "code" to exchange.code,
                "redirect_uri" to exchange.redirectUri,
                "code_verifier" to exchange.codeVerifier,
            ),
        ).toAuthResult()

    override suspend fun refresh(refreshToken: String): RefreshResult =
        request(
            mapOf(
                "grant_type" to "refresh_token",
                "client_id" to config.clientId,
                "refresh_token" to refreshToken,
            ),
        ).toRefreshResult()

    private suspend fun request(fields: Map<String, String>): GatewayResult =
        withContext(dispatchers.io) {
            val maxAttempts = retryDelaysMillis.size + 1
            for (attempt in 1..maxAttempts) {
                try {
                    return@withContext attemptRequest(fields)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: IOException) {
                    val isLastAttempt = attempt == maxAttempts
                    logger.warn(
                        TAG,
                        if (isLastAttempt) "token endpoint request failed, giving up" else "token endpoint request failed, retrying",
                        fields = mapOf("attempt" to attempt.toString(), "maxAttempts" to maxAttempts.toString()),
                        throwable = exception,
                    )
                    if (isLastAttempt) return@withContext GatewayResult.Network
                    retryDelay(retryDelaysMillis[attempt - 1])
                } catch (exception: Exception) {
                    logger.warn(TAG, "token endpoint response could not be parsed", throwable = exception)
                    return@withContext GatewayResult.Malformed
                }
            }
            error("unreachable: request() retry loop always returns before exhausting maxAttempts")
        }

    private fun attemptRequest(fields: Map<String, String>): GatewayResult {
        val body = FormBody.Builder().apply { fields.forEach(::add) }.build()
        val request =
            Request
                .Builder()
                .url(config.tokenEndpoint)
                .post(body)
                .build()
        return client.newCall(request).execute().use { response ->
            val responseBody = response.body.string()
            when {
                response.isSuccessful -> {
                    parseTokens(responseBody)
                }

                responseBody.errorCode() == INVALID_GRANT -> {
                    GatewayResult.InvalidGrant
                }

                else -> {
                    // A non-2xx response that isn't invalid_grant used to be collapsed into
                    // a generic "network" failure with the real reason thrown away. The
                    // OAuth "error" field (e.g. invalid_client, invalid_request) is not a
                    // secret; only the raw response body is withheld.
                    logger.warn(
                        TAG,
                        "token endpoint returned a non-success response",
                        fields =
                            mapOf(
                                "httpStatus" to response.code.toString(),
                                "error" to (responseBody.errorCode() ?: "unknown"),
                            ),
                    )
                    GatewayResult.Network
                }
            }
        }
    }

    private fun parseTokens(body: String): GatewayResult =
        try {
            val payload = Json.parseToJsonElement(body).jsonObject
            val accessToken = payload.stringValue(ACCESS_TOKEN) ?: return GatewayResult.Malformed
            val expiresIn = payload.longValue(EXPIRES_IN)
            GatewayResult.Success(
                AuthTokens(
                    accessToken = accessToken,
                    idToken = payload.stringValue(ID_TOKEN),
                    refreshToken = payload.stringValue(REFRESH_TOKEN),
                    accessTokenExpiresAtEpochSeconds = expiresIn?.let { nowEpochSeconds() + it },
                    earliestRefreshAtEpochSeconds = payload.longValue(EARLIEST_REFRESH_AT),
                    // This gateway is shared by both OAuth flows (browser and device code) and by
                    // refresh, so it cannot know the real method. The owning AuthProvider stamps
                    // the correct one on exchange, and TokenRefresher preserves it on refresh.
                    method = AuthMethod.CHATGPT_BROWSER,
                ),
            )
        } catch (exception: Exception) {
            logger.warn(TAG, "token endpoint success response could not be parsed", throwable = exception)
            GatewayResult.Malformed
        }

    private fun GatewayResult.toAuthResult(): AuthResult =
        when (this) {
            is GatewayResult.Success -> AuthResult.Success(tokens)
            GatewayResult.InvalidGrant -> AuthResult.Failure(AuthFailure.InvalidGrant)
            GatewayResult.Malformed -> AuthResult.Failure(AuthFailure.MalformedToken)
            GatewayResult.Network -> AuthResult.Failure(AuthFailure.Network(null))
        }

    private fun GatewayResult.toRefreshResult(): RefreshResult =
        when (this) {
            is GatewayResult.Success -> RefreshResult.Success(tokens)
            GatewayResult.InvalidGrant -> RefreshResult.Failure(AuthFailure.InvalidGrant)
            GatewayResult.Malformed -> RefreshResult.Failure(AuthFailure.MalformedToken)
            GatewayResult.Network -> RefreshResult.Failure(AuthFailure.Network(null))
        }

    private fun String.errorCode(): String? = runCatching { Json.parseToJsonElement(this).jsonObject.stringValue(ERROR) }.getOrNull()

    private sealed interface GatewayResult {
        data class Success(
            val tokens: AuthTokens,
        ) : GatewayResult

        data object InvalidGrant : GatewayResult

        data object Malformed : GatewayResult

        data object Network : GatewayResult
    }

    private companion object {
        private const val ACCESS_TOKEN: String = "access_token"
        private const val ID_TOKEN: String = "id_token"
        private const val REFRESH_TOKEN: String = "refresh_token"
        private const val EXPIRES_IN: String = "expires_in"
        private const val EARLIEST_REFRESH_AT: String = "earliest_refresh_at"
        private const val ERROR: String = "error"
        private const val INVALID_GRANT: String = "invalid_grant"
        private const val MILLIS_PER_SECOND: Long = 1_000L
        private const val FIRST_RETRY_DELAY_MILLIS: Long = 500L
        private const val SECOND_RETRY_DELAY_MILLIS: Long = 1_500L
    }
}

private fun JsonObject.stringValue(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.contentOrNull

private fun JsonObject.longValue(key: String): Long? = (this[key] as? JsonPrimitive)?.takeUnless(JsonPrimitive::isString)?.longOrNull
