package com.example.phoneagent.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.icons.AppIcons
import com.example.phoneagent.ui.designsystem.layout.contentColumn
import com.example.phoneagent.ui.designsystem.theme.AppTheme

private val EmptyMarkSize = 40.dp
private val SuggestionMaxWidth = 480.dp

/**
 * The empty chat: the mark, a title, one honest sentence about confirmations and three example
 * requests. Tapping a suggestion only hands its text to [onSuggestionClick] (the caller puts it into
 * the composer); nothing is sent until the user does. It scrolls, so in landscape or with the keyboard
 * open every suggestion is still reachable.
 */
@Composable
fun EmptyState(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier.contentColumn(SuggestionMaxWidth).padding(AppTheme.spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
        ) {
            Icon(
                painter = painterResource(AppIcons.brandMark),
                contentDescription = null,
                tint = AppTheme.extendedColors.brandMark,
                modifier = Modifier.size(EmptyMarkSize),
            )
            Text(
                text = stringResource(R.string.chat_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.chat_empty_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = AppTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
            ) {
                suggestions.forEach { suggestion ->
                    Suggestion(text = suggestion.trim(), onClick = { onSuggestionClick(suggestion) })
                }
            }
        }
    }
}

/** One example request on `surfaceContainer`: the whole text wraps, the row is at least 48dp tall. */
@Composable
private fun Suggestion(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = AppTheme.shapeTokens.card,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = AppTheme.spacing.medium, vertical = AppTheme.spacing.compact),
        )
    }
}
