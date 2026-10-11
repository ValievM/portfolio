package com.example.phoneagent.ui.designsystem.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.example.phoneagent.ui.designsystem.theme.AppTheme

/**
 * The frame every bottom sheet shares: a title that takes the accessibility focus (not the "approve"
 * button), a body that scrolls when it is long, and a footer that stays in place. At 200% font size or
 * with fifteen items the body scrolls and the footer never covers its last line and never disappears.
 */
@Composable
internal fun SheetLayout(
    title: String,
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    body: @Composable ColumnScope.() -> Unit,
) {
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { titleFocus.requestFocus() }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.spacing.large)
                .padding(bottom = AppTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocus)
                    .focusable()
                    .semantics { heading() },
        )
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
            content = body,
        )
        footer()
    }
}

/**
 * A confirmation sheet that opens at the height of its content (up to the full screen) and never in
 * Material's half-open state: in that state the footer with "Разрешить" and "Отклонить" could sit below
 * the visible edge, out of reach. Closing it any way (back, swipe, scrim) reports [onDismissRequest].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConfirmationSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        content = content,
    )
}
