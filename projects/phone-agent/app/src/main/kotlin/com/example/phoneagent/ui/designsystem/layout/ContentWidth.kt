package com.example.phoneagent.ui.designsystem.layout

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Reading width of the chat and of forms: on a tablet or in landscape the column stays this wide, centered. */
val ContentMaxWidth: Dp = 640.dp

private val NARROW_SCREEN_WIDTH = 360.dp
private val WIDE_MARGIN = 20.dp
private val NARROW_MARGIN = 16.dp

/**
 * Fills the available width up to [maxWidth] and centers the result. Order matters: the first
 * `fillMaxWidth` fixes the slot, `wrapContentWidth` centers whatever is inside, `widthIn` caps it and
 * the last `fillMaxWidth` makes the content use the whole capped width.
 */
fun Modifier.contentColumn(maxWidth: Dp = ContentMaxWidth): Modifier =
    fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = maxWidth)
        .fillMaxWidth()

/** Side margin of a screen: 20dp, or 16dp on screens narrower than 360dp so text keeps its room. */
@Composable
@ReadOnlyComposable
fun screenMargin(): Dp = if (LocalConfiguration.current.screenWidthDp.dp < NARROW_SCREEN_WIDTH) NARROW_MARGIN else WIDE_MARGIN
