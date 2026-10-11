package com.example.phoneagent.data.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.util.Base64

public data class JwtClaims(
    public val accessTokenExpiresAtEpochSeconds: Long,
    public val chatGptAccountId: String?,
)

public sealed interface JwtClaimsResult {
    public data class Success(
        public val claims: JwtClaims,
    ) : JwtClaimsResult

    public data class Failure(
        public val failure: AuthFailure,
    ) : JwtClaimsResult
}

public object JwtClaimsExtractor {
    private const val OPEN_AI_AUTH_CLAIM = "https://api.openai.com/auth"
    private const val ACCOUNT_ID_CLAIM = "chatgpt_account_id"
    private const val JWT_PART_COUNT = 3

    public fun extract(
        accessToken: String,
        idToken: String?,
    ): JwtClaimsResult {
        val accessPayload = parsePayload(accessToken)
        val expiresAt = numberValue(accessPayload?.get("exp"))
        return if (accessPayload == null || expiresAt == null) {
            JwtClaimsResult.Failure(AuthFailure.MalformedToken)
        } else {
            val accountId = accountId(idToken?.let(::parsePayload)) ?: accountId(accessPayload)
            JwtClaimsResult.Success(JwtClaims(expiresAt, accountId))
        }
    }

    private fun accountId(payload: JsonObject?): String? {
        payload ?: return null
        return stringValue(payload[ACCOUNT_ID_CLAIM])
            ?: stringValue((payload[OPEN_AI_AUTH_CLAIM] as? JsonObject)?.get(ACCOUNT_ID_CLAIM))
    }

    private fun numberValue(element: JsonElement?): Long? = (element as? JsonPrimitive)?.takeUnless(JsonPrimitive::isString)?.longOrNull

    private fun stringValue(element: JsonElement?): String? = (element as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content

    private fun parsePayload(token: String): JsonObject? =
        runCatching {
            val parts = token.split('.')
            require(parts.size == JWT_PART_COUNT)
            val decoded = Base64.getUrlDecoder().decode(parts[1])
            Json.parseToJsonElement(decoded.toString(Charsets.UTF_8)).jsonObject
        }.getOrNull()
}
