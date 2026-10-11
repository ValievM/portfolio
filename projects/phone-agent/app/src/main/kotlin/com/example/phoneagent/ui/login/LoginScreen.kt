package com.example.phoneagent.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.components.NoticeTone
import com.example.phoneagent.ui.designsystem.components.PrimaryButton
import com.example.phoneagent.ui.designsystem.components.QuietButton
import com.example.phoneagent.ui.designsystem.components.SecondaryButton
import com.example.phoneagent.ui.designsystem.components.StatusMessage
import com.example.phoneagent.ui.designsystem.icons.AppIcons
import com.example.phoneagent.ui.designsystem.layout.contentColumn
import com.example.phoneagent.ui.designsystem.layout.screenMargin
import com.example.phoneagent.ui.designsystem.theme.AppCodeTextStyle
import com.example.phoneagent.ui.designsystem.theme.AppTheme

@Composable
fun LoginRoute(
    authenticatedContent: @Composable (onSignOut: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showApiKey by rememberSaveable { mutableStateOf(false) }
    // Not rememberSaveable: the raw key must not be serialized into the saved-instance Bundle
    // (persisted to disk across process death). It only needs to survive recompositions.
    var apiKey by remember { mutableStateOf("") }

    if (state.isSignedIn) {
        authenticatedContent(viewModel::signOut)
    } else {
        LoginScreen(
            state = state,
            form = LoginFormState(showApiKey, apiKey),
            onEvent = { event ->
                when (event) {
                    is LoginUiEvent.ApiKeyChanged -> {
                        apiKey = event.value
                    }

                    LoginUiEvent.ShowApiKey -> {
                        showApiKey = true
                    }

                    LoginUiEvent.BrowserSignIn -> {
                        viewModel.signInWithBrowser()
                    }

                    LoginUiEvent.ApiKeySignIn -> {
                        viewModel.signInWithApiKey(apiKey)
                        apiKey = ""
                    }

                    LoginUiEvent.DeviceCodeSignIn -> {
                        viewModel.signInWithDeviceCode()
                    }

                    LoginUiEvent.OpenDevicePage -> {
                        viewModel.openDeviceVerificationPage()
                    }
                }
            },
            modifier = modifier,
        )
    }
}

@Composable
fun LoginScreen(
    state: LoginUiState,
    form: LoginFormState,
    onEvent: (LoginUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier) { contentPadding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(contentPadding).imePadding()) {
            val viewport = maxHeight
            // Wide and low (a phone turned sideways, a tablet): the introduction and the actions go side by side.
            val twoPane = maxWidth >= TWO_PANE_MIN_WIDTH && maxWidth > maxHeight
            // The content is centered when it is short and scrolls when it is not (large font, landscape, keyboard).
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                val contentModifier =
                    Modifier
                        .heightIn(min = viewport)
                        .contentColumn(maxWidth = if (twoPane) TWO_PANE_MAX_WIDTH else LOGIN_MAX_WIDTH)
                        .padding(horizontal = screenMargin(), vertical = AppTheme.spacing.large)
                if (twoPane) {
                    Row(
                        modifier = contentModifier,
                        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.extraLarge),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LoginIntro(Modifier.weight(1f))
                        LoginBody(state, form, onEvent, Modifier.weight(1f))
                    }
                } else {
                    Column(
                        modifier = contentModifier,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium, Alignment.CenterVertically),
                    ) {
                        LoginIntro(Modifier.fillMaxWidth())
                        LoginBody(state, form, onEvent, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

private val BRAND_MARK_SIZE = 64.dp
private val LOGIN_MAX_WIDTH = 480.dp
private val TWO_PANE_MIN_WIDTH = 600.dp
private val TWO_PANE_MAX_WIDTH = 920.dp

/** The mark, the title and what is stored and sent: this comes before the buttons, so the choice is an informed one. */
@Composable
private fun LoginIntro(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
    ) {
        Icon(
            painter = painterResource(AppIcons.brandMark),
            contentDescription = null,
            modifier = Modifier.size(BRAND_MARK_SIZE),
            tint = AppTheme.extendedColors.brandMark,
        )
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.login_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.login_storage_notice),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LoginBody(
    state: LoginUiState,
    form: LoginFormState,
    onEvent: (LoginUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
    ) {
        if (state.sessionExpired) {
            StatusMessage(stringResource(R.string.login_session_expired), tone = NoticeTone.Warning)
        }
        state.error?.let { error -> StatusMessage(stringResource(error.messageResource())) }
        state.deviceCode?.let { code ->
            DeviceCodeCard(
                code = code,
                verificationUrl = state.deviceVerificationUrl.orEmpty(),
                onOpen = { onEvent(LoginUiEvent.OpenDevicePage) },
            )
        }

        LoginActions(state, form, onEvent)

        if (state.showDeviceCodeFallback) {
            QuietButton(onClick = { onEvent(LoginUiEvent.DeviceCodeSignIn) }, enabled = !state.isLoading) {
                Text(stringResource(R.string.login_use_device_code))
            }
        }
        if (state.isLoading && state.deviceCode == null) CircularProgressIndicator()
    }
}

@Composable
private fun LoginActions(
    state: LoginUiState,
    form: LoginFormState,
    onEvent: (LoginUiEvent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
    ) {
        if (form.showApiKey) {
            ApiKeyForm(
                apiKey = form.apiKey,
                enabled = !state.isLoading,
                onApiKeyChange = { onEvent(LoginUiEvent.ApiKeyChanged(it)) },
                onSubmit = { onEvent(LoginUiEvent.ApiKeySignIn) },
            )
        } else {
            PrimaryButton(
                onClick = { onEvent(LoginUiEvent.BrowserSignIn) },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (state.sessionExpired) R.string.login_again else R.string.login_openai))
            }
            SecondaryButton(
                onClick = { onEvent(LoginUiEvent.ShowApiKey) },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.login_use_api_key))
            }
        }
    }
}

@Composable
private fun ApiKeyForm(
    apiKey: String,
    enabled: Boolean,
    onApiKeyChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small)) {
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            shape = AppTheme.shapeTokens.button,
            label = { Text(stringResource(R.string.login_api_key_label)) },
            placeholder = { Text(stringResource(R.string.login_api_key_placeholder)) },
            supportingText = { Text(stringResource(R.string.login_api_key_helper)) },
            visualTransformation = PasswordVisualTransformation(),
        )
        PrimaryButton(onClick = onSubmit, enabled = enabled && apiKey.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.login_api_key_submit))
        }
    }
}

@Composable
private fun DeviceCodeCard(
    code: String,
    verificationUrl: String,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppTheme.shapeTokens.card,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(AppTheme.spacing.medium), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small)) {
            Text(stringResource(R.string.login_device_code_title), style = MaterialTheme.typography.titleMedium)
            SelectionContainer {
                Text(code, style = AppCodeTextStyle.copy(fontSize = DEVICE_CODE_SIZE, lineHeight = DEVICE_CODE_LINE_HEIGHT))
            }
            Text(verificationUrl, style = AppCodeTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.login_device_code_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.login_open_device_page)) }
        }
    }
}

private val DEVICE_CODE_SIZE = 28.sp
private val DEVICE_CODE_LINE_HEIGHT = 36.sp

private fun LoginError.messageResource(): Int =
    when (this) {
        LoginError.INVALID_API_KEY -> R.string.login_error_invalid_api_key
        LoginError.LOOPBACK_PORT_UNAVAILABLE -> R.string.login_error_port_busy
        LoginError.TIMEOUT -> R.string.login_error_timeout
        LoginError.NETWORK -> R.string.login_error_network
        LoginError.GENERIC -> R.string.login_error_generic
    }
