package com.example.phoneagent.auth

import com.example.phoneagent.data.auth.AuthFailure
import com.example.phoneagent.data.auth.AuthMethod
import com.example.phoneagent.data.auth.AuthProvider
import com.example.phoneagent.data.auth.AuthResult
import com.example.phoneagent.data.auth.AuthState
import com.example.phoneagent.data.auth.BrowserLauncher
import com.example.phoneagent.data.auth.ChatGptDeviceCodeAuth
import com.example.phoneagent.data.auth.DeviceCodePrompt
import com.example.phoneagent.data.auth.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionAuthCoordinator
    @Inject
    constructor(
        private val manager: SessionManager,
        private val apiKeyProvider: MutableApiKeyAuthProvider,
        private val deviceCodeAuth: ChatGptDeviceCodeAuth,
        private val browserLauncher: BrowserLauncher,
        applicationScope: CoroutineScope,
    ) : AuthCoordinator {
        override val state: StateFlow<AppAuthStatus> =
            combine(manager.state, manager.reauthenticationFailure, deviceCodeAuth.prompt, ::mapStatus)
                .stateIn(applicationScope, SharingStarted.Eagerly, AppAuthStatus.Restoring)

        override suspend fun restore() = manager.restore()

        override suspend fun signInWithBrowser() {
            manager.signIn(AuthMethod.CHATGPT_BROWSER)
        }

        override suspend fun signInWithDeviceCode() {
            manager.signIn(AuthMethod.CHATGPT_DEVICE_CODE)
        }

        override suspend fun signInWithApiKey(key: String) {
            apiKeyProvider.setKey(key)
            manager.signIn(AuthMethod.API_KEY)
        }

        override suspend fun signOut() = manager.signOut()

        override fun openDeviceVerificationPage() {
            deviceCodeAuth.prompt.value
                ?.verificationUrl
                ?.let(browserLauncher::open)
        }

        private fun mapStatus(
            authState: AuthState,
            reauthenticationFailure: AuthFailure?,
            prompt: DeviceCodePrompt?,
        ): AppAuthStatus =
            when (authState) {
                AuthState.SignedOut -> {
                    AppAuthStatus.SignedOut(sessionExpired = reauthenticationFailure != null)
                }

                is AuthState.SigningIn -> {
                    prompt?.let { AppAuthStatus.DeviceCode(it.userCode, it.verificationUrl) }
                        ?: AppAuthStatus.SigningIn
                }

                is AuthState.SignedIn -> {
                    AppAuthStatus.SignedIn(authState.method)
                }

                is AuthState.Error -> {
                    AppAuthStatus.Failure(authState.failure.toUiReason())
                }
            }

        private fun AuthFailure.toUiReason(): AuthErrorReason =
            when (this) {
                AuthFailure.InvalidApiKey -> AuthErrorReason.INVALID_API_KEY
                AuthFailure.LoopbackPortUnavailable -> AuthErrorReason.LOOPBACK_PORT_UNAVAILABLE
                AuthFailure.TimedOut -> AuthErrorReason.TIMEOUT
                is AuthFailure.Network -> AuthErrorReason.NETWORK
                else -> AuthErrorReason.GENERIC
            }
    }

@Singleton
class MutableApiKeyAuthProvider
    @Inject
    constructor(
        private val factory: ApiKeyAuthFactory,
    ) : AuthProvider {
        override val method: AuthMethod = AuthMethod.API_KEY
        private var pendingKey: String? = null

        fun setKey(key: String) {
            pendingKey = key
        }

        override suspend fun signIn(): AuthResult =
            try {
                factory.create(pendingKey.orEmpty()).signIn()
            } finally {
                pendingKey = null
            }
    }

fun interface ApiKeyAuthFactory {
    fun create(key: String): AuthProvider
}
