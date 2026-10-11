package com.example.phoneagent.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.warn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val DATA_STORE_NAME = "auth_tokens"
private const val KEY_ALIAS = "phone_agent_auth_tokens"
private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
private const val KEY_ALGORITHM = "AES"
private const val IV_BYTES = 12
private const val GCM_TAG_LENGTH_BITS = 128
private const val KEY_SIZE_BITS = 256
private const val ACCESS_TOKEN = "access_token"
private const val ID_TOKEN = "id_token"
private const val REFRESH_TOKEN = "refresh_token"
private const val EXPIRES_AT = "expires_at"
private const val EARLIEST_REFRESH_AT = "earliest_refresh_at"
private const val METHOD = "method"
private const val TAG = "EncryptedTokenStore"

private val Context.authTokenDataStore: DataStore<Preferences> by preferencesDataStore(name = DATA_STORE_NAME)
private val encryptedTokensKey = stringPreferencesKey("encrypted_tokens")

public class EncryptedTokenStore(
    context: Context,
    private val logger: AppLogger,
) : TokenStore {
    private val dataStore: DataStore<Preferences> = context.applicationContext.authTokenDataStore
    private val cipher: AndroidKeystoreAesGcm = AndroidKeystoreAesGcm()

    override suspend fun read(): AuthTokens? =
        try {
            dataStore.data.first()[encryptedTokensKey]?.let { encryptedTokens ->
                deserialize(cipher.decrypt(encryptedTokens))
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            // Silently treating this as "signed out" is the right user-facing behavior (a
            // corrupted entry shouldn't crash the app), but without a log line a vanished
            // session looks like a mystery instead of, say, a Keystore key wipe.
            logger.warn(TAG, "failed to read the stored session, treating it as signed out", throwable = exception)
            null
        }

    override suspend fun write(tokens: AuthTokens) {
        val encryptedTokens = cipher.encrypt(serialize(tokens))
        dataStore.edit { preferences -> preferences[encryptedTokensKey] = encryptedTokens }
    }

    override suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(encryptedTokensKey) }
    }

    private fun serialize(tokens: AuthTokens): String =
        buildJsonObject {
            put(ACCESS_TOKEN, tokens.accessToken)
            tokens.idToken?.let { put(ID_TOKEN, it) }
            tokens.refreshToken?.let { put(REFRESH_TOKEN, it) }
            tokens.accessTokenExpiresAtEpochSeconds?.let { put(EXPIRES_AT, it) }
            tokens.earliestRefreshAtEpochSeconds?.let { put(EARLIEST_REFRESH_AT, it) }
            put(METHOD, tokens.method.name)
        }.toString()

    private fun deserialize(serializedTokens: String): AuthTokens? {
        val payload = Json.parseToJsonElement(serializedTokens).jsonObject
        val accessToken = payload.stringValue(ACCESS_TOKEN)
        // Missing or unrecognized method means data from before this field existed, or from a
        // future/rolled-back app version: treat it as unreadable rather than guessing.
        val method = payload.stringValue(METHOD)?.let { runCatching { AuthMethod.valueOf(it) }.getOrNull() }
        if (accessToken == null || method == null) return null
        return AuthTokens(
            accessToken = accessToken,
            idToken = payload.stringValue(ID_TOKEN),
            refreshToken = payload.stringValue(REFRESH_TOKEN),
            accessTokenExpiresAtEpochSeconds = payload.longValue(EXPIRES_AT),
            earliestRefreshAtEpochSeconds = payload.longValue(EARLIEST_REFRESH_AT),
            method = method,
        )
    }
}

private fun JsonObject.stringValue(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content

private fun JsonObject.longValue(key: String): Long? = (this[key] as? JsonPrimitive)?.takeUnless(JsonPrimitive::isString)?.longOrNull

private class AndroidKeystoreAesGcm {
    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(cipher.iv + encryptedBytes)
    }

    fun decrypt(encryptedText: String): String {
        val bytes = Base64.getUrlDecoder().decode(encryptedText)
        require(bytes.size > IV_BYTES)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, bytes.copyOfRange(0, IV_BYTES)),
        )
        return cipher.doFinal(bytes.copyOfRange(IV_BYTES, bytes.size)).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        return (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: createKey()
    }

    private fun createKey(): SecretKey =
        KeyGenerator
            .getInstance(KEY_ALGORITHM, KEYSTORE_PROVIDER)
            .apply {
                init(
                    KeyGenParameterSpec
                        .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(KEY_SIZE_BITS)
                        .build(),
                )
            }.generateKey()
}
