package com.example.phoneagent.ui.login

data class LoginUiState(
    val isSignedIn: Boolean = false,
    val isLoading: Boolean = false,
    val sessionExpired: Boolean = false,
    val error: LoginError? = null,
    val deviceCode: String? = null,
    val deviceVerificationUrl: String? = null,
    val showDeviceCodeFallback: Boolean = false,
)

data class LoginFormState(
    val showApiKey: Boolean = false,
    val apiKey: String = "",
)

enum class LoginError {
    INVALID_API_KEY,
    LOOPBACK_PORT_UNAVAILABLE,
    TIMEOUT,
    NETWORK,
    GENERIC,
}

sealed interface LoginUiEvent {
    data class ApiKeyChanged(
        val value: String,
    ) : LoginUiEvent

    data object ShowApiKey : LoginUiEvent

    data object BrowserSignIn : LoginUiEvent

    data object ApiKeySignIn : LoginUiEvent

    data object DeviceCodeSignIn : LoginUiEvent

    data object OpenDevicePage : LoginUiEvent
}
