package com.example.phoneagent.ui.login

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.phoneagent.ui.designsystem.theme.AppTheme

@Preview(showBackground = true)
@Composable
private fun LoginPreview() = AppTheme { PreviewLogin(LoginUiState()) }

@Preview(showBackground = true)
@Composable
private fun LoginLoadingPreview() = AppTheme { PreviewLogin(LoginUiState(isLoading = true)) }

@Preview(showBackground = true)
@Composable
private fun LoginErrorPreview() = AppTheme { PreviewLogin(LoginUiState(error = LoginError.NETWORK)) }

@Preview(showBackground = true)
@Composable
private fun LoginExpiredPreview() = AppTheme { PreviewLogin(LoginUiState(sessionExpired = true)) }

@Composable
private fun PreviewLogin(state: LoginUiState) {
    LoginScreen(state, LoginFormState(), {})
}
