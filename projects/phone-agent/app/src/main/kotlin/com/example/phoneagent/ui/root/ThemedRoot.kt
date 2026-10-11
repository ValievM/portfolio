package com.example.phoneagent.ui.root

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.components.NoticeTone
import com.example.phoneagent.ui.designsystem.components.PrimaryButton
import com.example.phoneagent.ui.designsystem.components.StatusBanner
import com.example.phoneagent.ui.designsystem.icons.AppIcons
import com.example.phoneagent.ui.designsystem.layout.contentColumn
import com.example.phoneagent.ui.designsystem.theme.AppTheme

private val ROOT_MARK_SIZE = 40.dp

/**
 * Wraps the whole app and shows the settings state: a neutral wait while nothing is read yet, an
 * explicit retry when the very first read failed, and a dismissible notice above the running app
 * when a later read failed (the last known theme stays in place). [content] is never recreated
 * because of a theme change.
 */
@Composable
fun ThemedRoot(
    state: ThemeState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        when {
            state is ThemeState.Loading -> {
                LoadingScreen()
            }

            state is ThemeState.Unavailable && state.lastKnown == null -> {
                SettingsLoadError(onRetry = onRetry)
            }

            else -> {
                content()
                StaleSettingsNotice(visible = state is ThemeState.Unavailable, onRetry = onRetry)
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SettingsLoadError(onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.contentColumn(maxWidth = 480.dp).padding(AppTheme.spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
        ) {
            Icon(
                painter = painterResource(AppIcons.brandMark),
                contentDescription = null,
                tint = AppTheme.extendedColors.brandMark,
                modifier = Modifier.size(ROOT_MARK_SIZE),
            )
            Text(
                text = stringResource(R.string.theme_load_failed),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(onClick = onRetry) { Text(stringResource(R.string.error_retry)) }
        }
    }
}

@Composable
private fun BoxScope.StaleSettingsNotice(
    visible: Boolean,
    onRetry: () -> Unit,
) {
    var dismissed by remember(visible) { mutableStateOf(false) }
    AnimatedVisibility(
        visible = visible && !dismissed,
        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        StatusBanner(
            message = stringResource(R.string.theme_settings_stale),
            onDismiss = { dismissed = true },
            onRetry = onRetry,
            tone = NoticeTone.Warning,
        )
    }
}
