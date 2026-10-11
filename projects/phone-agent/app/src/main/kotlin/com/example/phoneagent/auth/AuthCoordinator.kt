package com.example.phoneagent.auth

import com.example.phoneagent.data.auth.AuthMethod
import kotlinx.coroutines.flow.StateFlow

sealed interface AppAuthStatus {
    data object Restoring : AppAuthStatus

    data class SignedOut(
        val sessionExpired: Boolean = false,
    ) : AppAuthStatus

    data object SigningIn : AppAuthStatus

    data class DeviceCode(
        val userCode: String,
        val verificationUrl: String,
    ) : AppAuthStatus

    data class SignedIn(
        val method: AuthMethod,
    ) : AppAuthStatus

    data class Failure(
        val reason: AuthErrorReason,
    ) : AppAuthStatus
}

enum class AuthErrorReason {
    INVALID_API_KEY,
    LOOPBACK_PORT_UNAVAILABLE,
    TIMEOUT,
    NETWORK,
    GENERIC,
}

enum class AppSignInMethod {
    Browser,
    DeviceCode,
    ApiKey,
}

internal fun AuthMethod.toAppSignInMethod(): AppSignInMethod =
    when (this) {
        AuthMethod.CHATGPT_BROWSER -> AppSignInMethod.Browser
        AuthMethod.CHATGPT_DEVICE_CODE -> AppSignInMethod.DeviceCode
        AuthMethod.API_KEY -> AppSignInMethod.ApiKey
    }

interface AuthCoordinator {
    val state: StateFlow<AppAuthStatus>

    suspend fun restore()

    suspend fun signInWithBrowser()

    suspend fun signInWithDeviceCode()

    suspend fun signInWithApiKey(key: String)

    suspend fun signOut()

    fun openDeviceVerificationPage()
}
