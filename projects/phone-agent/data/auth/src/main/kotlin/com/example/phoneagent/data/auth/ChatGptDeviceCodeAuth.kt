package com.example.phoneagent.data.auth

import com.example.phoneagent.domain.platform.DefaultDispatcherProvider
import com.example.phoneagent.domain.platform.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

public data class DeviceCodePrompt(
    public val userCode: String,
    public val verificationUrl: String,
)

public sealed interface DeviceCodeRequestResult {
    public data class Ready(
        public val deviceAuthId: String,
        public val userCode: String,
        public val verificationUrl: String,
        public val intervalSeconds: Long,
    ) : DeviceCodeRequestResult

    public data class Failure(
        val failure: AuthFailure,
    ) : DeviceCodeRequestResult
}

public sealed interface DeviceCodePollResult {
    public data object Pending : DeviceCodePollResult

    public data class Authorized(
        public val authorizationCode: String,
        public val codeVerifier: String,
    ) : DeviceCodePollResult

    public data class Failure(
        val failure: AuthFailure,
    ) : DeviceCodePollResult
}

public interface DeviceCodeGateway {
    public suspend fun requestCode(): DeviceCodeRequestResult

    public suspend fun poll(
        deviceAuthId: String,
        userCode: String,
    ): DeviceCodePollResult
}

public class ChatGptDeviceCodeAuth(
    private val config: AuthConfig,
    private val gateway: DeviceCodeGateway,
    private val tokenGateway: TokenRefreshGateway,
    private val timeoutMillis: Long = DEVICE_CODE_TIMEOUT_MILLIS,
) : AuthProvider {
    override val method: AuthMethod = AuthMethod.CHATGPT_DEVICE_CODE

    private val mutablePrompt = MutableStateFlow<DeviceCodePrompt?>(null)
    public val prompt: StateFlow<DeviceCodePrompt?> = mutablePrompt.asStateFlow()

    override suspend fun signIn(): AuthResult {
        val code =
            when (val result = gateway.requestCode()) {
                is DeviceCodeRequestResult.Failure -> return AuthResult.Failure(result.failure)
                is DeviceCodeRequestResult.Ready -> result
            }
        mutablePrompt.value = DeviceCodePrompt(code.userCode, code.verificationUrl)
        return try {
            withTimeoutOrNull(timeoutMillis) { pollUntilAuthorized(code) }
                ?: AuthResult.Failure(AuthFailure.TimedOut)
        } finally {
            mutablePrompt.value = null
        }
    }

    private suspend fun pollUntilAuthorized(code: DeviceCodeRequestResult.Ready): AuthResult {
        while (true) {
            when (val result = gateway.poll(code.deviceAuthId, code.userCode)) {
                DeviceCodePollResult.Pending -> {
                    delay(code.intervalSeconds.coerceAtLeast(1L) * MILLIS_PER_SECOND)
                }

                is DeviceCodePollResult.Failure -> {
                    return AuthResult.Failure(result.failure)
                }

                is DeviceCodePollResult.Authorized -> {
                    return tokenGateway
                        .exchangeAuthorizationCode(
                            AuthorizationCodeExchange(result.authorizationCode, result.codeVerifier, config.deviceCallbackUrl),
                        ).withMethod(AuthMethod.CHATGPT_DEVICE_CODE)
                }
            }
        }
    }

    private companion object {
        private const val DEVICE_CODE_TIMEOUT_MILLIS: Long = 15 * 60 * 1_000L
        private const val MILLIS_PER_SECOND: Long = 1_000L
    }
}

public class OkHttpDeviceCodeGateway(
    private val config: AuthConfig,
    private val client: OkHttpClient = OkHttpClient(),
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
) : DeviceCodeGateway {
    override suspend fun requestCode(): DeviceCodeRequestResult =
        execute(
            config.deviceCodeEndpoint,
            buildJsonObject { put(CLIENT_ID, config.clientId) }.toString(),
            onIoException = { DeviceCodeRequestResult.Failure(AuthFailure.Network(null)) },
        ) { code, body ->
            if (code !in SUCCESS_RANGE) return@execute DeviceCodeRequestResult.Failure(AuthFailure.Network(null))
            val payload =
                body.jsonObjectOrNull()
                    ?: return@execute DeviceCodeRequestResult.Failure(AuthFailure.MalformedToken)
            val deviceAuthId = payload.stringValue(DEVICE_AUTH_ID)
            val userCode = payload.stringValue(USER_CODE) ?: payload.stringValue(USER_CODE_ALIAS)
            val interval = payload.longValue(INTERVAL) ?: payload.stringValue(INTERVAL)?.toLongOrNull()
            if (deviceAuthId == null || userCode == null || interval == null) {
                DeviceCodeRequestResult.Failure(AuthFailure.MalformedToken)
            } else {
                DeviceCodeRequestResult.Ready(deviceAuthId, userCode, config.deviceVerificationUrl, interval)
            }
        }

    override suspend fun poll(
        deviceAuthId: String,
        userCode: String,
    ): DeviceCodePollResult =
        execute(
            config.deviceTokenEndpoint,
            buildJsonObject {
                put(DEVICE_AUTH_ID, deviceAuthId)
                put(USER_CODE, userCode)
            }.toString(),
            onIoException = { DeviceCodePollResult.Failure(AuthFailure.Network(null)) },
        ) { code, body ->
            when {
                code == FORBIDDEN || code == NOT_FOUND -> {
                    DeviceCodePollResult.Pending
                }

                code !in SUCCESS_RANGE -> {
                    DeviceCodePollResult.Failure(AuthFailure.Network(null))
                }

                else -> {
                    val payload =
                        body.jsonObjectOrNull()
                            ?: return@execute DeviceCodePollResult.Failure(AuthFailure.MalformedToken)
                    val authorizationCode = payload.stringValue(AUTHORIZATION_CODE)
                    val verifier = payload.stringValue(CODE_VERIFIER)
                    if (authorizationCode == null || verifier == null) {
                        DeviceCodePollResult.Failure(AuthFailure.MalformedToken)
                    } else {
                        DeviceCodePollResult.Authorized(authorizationCode, verifier)
                    }
                }
            }
        }

    private suspend fun <T> execute(
        url: String,
        jsonBody: String,
        onIoException: () -> T,
        map: (Int, String) -> T,
    ): T =
        withContext(dispatchers.io) {
            try {
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                        .build()
                client.newCall(request).execute().use { response -> map(response.code, response.body.string()) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: IOException) {
                onIoException()
            }
        }

    private fun String.jsonObjectOrNull(): JsonObject? = runCatching { Json.parseToJsonElement(this).jsonObject }.getOrNull()

    private companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val SUCCESS_RANGE: IntRange = 200..299
        private const val FORBIDDEN: Int = 403
        private const val NOT_FOUND: Int = 404
        private const val CLIENT_ID: String = "client_id"
        private const val DEVICE_AUTH_ID: String = "device_auth_id"
        private const val USER_CODE: String = "user_code"
        private const val USER_CODE_ALIAS: String = "usercode"
        private const val INTERVAL: String = "interval"
        private const val AUTHORIZATION_CODE: String = "authorization_code"
        private const val CODE_VERIFIER: String = "code_verifier"
    }
}

private fun JsonObject.stringValue(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.contentOrNull

private fun JsonObject.longValue(key: String): Long? =
    (this[key] as? JsonPrimitive)?.takeUnless(JsonPrimitive::isString)?.contentOrNull?.toLongOrNull()
