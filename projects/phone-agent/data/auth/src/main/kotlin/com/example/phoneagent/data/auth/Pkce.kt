package com.example.phoneagent.data.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

public fun interface AuthEntropy {
    public fun nextBytes(size: Int): ByteArray
}

public object SecureAuthEntropy : AuthEntropy {
    private val secureRandom: SecureRandom = SecureRandom()

    override fun nextBytes(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)
}

public data class PkceParameters(
    public val verifier: String,
    public val challenge: String,
    public val state: String,
)

public object Pkce {
    private const val VERIFIER_BYTES = 32
    private val VERIFIER_PATTERN: Regex = Regex("[A-Za-z0-9\\-._~]{43,128}")

    public fun create(entropy: AuthEntropy = SecureAuthEntropy): PkceParameters {
        val verifier = encodeUrl(entropy.nextBytes(VERIFIER_BYTES))
        val state = encodeUrl(entropy.nextBytes(VERIFIER_BYTES))
        return PkceParameters(
            verifier = verifier,
            challenge = challenge(verifier),
            state = state,
        )
    }

    public fun challenge(verifier: String): String {
        require(isValidVerifier(verifier)) { "Invalid PKCE verifier" }
        return encodeUrl(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    }

    public fun isValidVerifier(verifier: String): Boolean = VERIFIER_PATTERN.matches(verifier)

    private fun encodeUrl(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
