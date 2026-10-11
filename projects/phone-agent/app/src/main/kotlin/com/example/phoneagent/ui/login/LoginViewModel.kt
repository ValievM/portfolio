package com.example.phoneagent.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.phoneagent.auth.AppAuthStatus
import com.example.phoneagent.auth.AuthCoordinator
import com.example.phoneagent.auth.AuthErrorReason
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val coordinator: AuthCoordinator,
    ) : ViewModel() {
        val state: StateFlow<LoginUiState> =
            coordinator.state
                .map(::toUiState)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), LoginUiState(isLoading = true))

        init {
            viewModelScope.launch { coordinator.restore() }
        }

        fun signInWithBrowser() {
            viewModelScope.launch { coordinator.signInWithBrowser() }
        }

        fun signInWithDeviceCode() {
            viewModelScope.launch { coordinator.signInWithDeviceCode() }
        }

        fun signInWithApiKey(key: String) {
            viewModelScope.launch { coordinator.signInWithApiKey(key) }
        }

        fun signOut() {
            viewModelScope.launch { coordinator.signOut() }
        }

        fun openDeviceVerificationPage() {
            coordinator.openDeviceVerificationPage()
        }

        private fun toUiState(status: AppAuthStatus): LoginUiState =
            when (status) {
                AppAuthStatus.Restoring -> {
                    LoginUiState(isLoading = true)
                }

                is AppAuthStatus.SignedOut -> {
                    LoginUiState(sessionExpired = status.sessionExpired)
                }

                AppAuthStatus.SigningIn -> {
                    LoginUiState(isLoading = true)
                }

                is AppAuthStatus.DeviceCode -> {
                    LoginUiState(
                        isLoading = true,
                        deviceCode = status.userCode,
                        deviceVerificationUrl = status.verificationUrl,
                    )
                }

                is AppAuthStatus.SignedIn -> {
                    LoginUiState(isSignedIn = true)
                }

                is AppAuthStatus.Failure -> {
                    LoginUiState(
                        error = status.reason.toLoginError(),
                        showDeviceCodeFallback = status.reason == AuthErrorReason.LOOPBACK_PORT_UNAVAILABLE,
                    )
                }
            }

        private fun AuthErrorReason.toLoginError(): LoginError = LoginError.valueOf(name)
    }
