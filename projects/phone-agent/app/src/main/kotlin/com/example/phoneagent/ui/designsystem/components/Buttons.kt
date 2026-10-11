package com.example.phoneagent.ui.designsystem.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.phoneagent.ui.designsystem.theme.AppTheme

// Material 3 has no theme-level button shape, so these three carry the brand contract (16dp radius,
// 48dp minimum height, 12dp vertical padding) in one place. The height only grows: at 200% font the
// label wraps and the button gets taller instead of clipping.
private val ButtonMinHeight = 48.dp
private val ButtonPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
private val QuietButtonPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

/** The main action of a screen or a sheet (`primary` on `onPrimary`). */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = ButtonMinHeight),
        enabled = enabled,
        shape = AppTheme.shapeTokens.button,
        contentPadding = ButtonPadding,
        content = content,
    )
}

/** The counterpart of a [PrimaryButton]: the same size, outlined (for example "Отклонить"). */
@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = ButtonMinHeight),
        enabled = enabled,
        shape = AppTheme.shapeTokens.button,
        contentPadding = ButtonPadding,
        content = content,
    )
}

/** A quiet action next to text ("Повторить", "Закрыть"): no fill, no outline. */
@Composable
fun QuietButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = QuietButtonPadding,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = ButtonMinHeight),
        enabled = enabled,
        shape = AppTheme.shapeTokens.button,
        colors = colors,
        contentPadding = contentPadding,
        content = content,
    )
}
