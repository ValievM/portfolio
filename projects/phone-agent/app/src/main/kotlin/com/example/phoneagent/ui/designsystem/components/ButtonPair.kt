package com.example.phoneagent.ui.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlin.math.max

/**
 * Two actions: side by side when both labels fit on one line, otherwise stacked with the [primary]
 * one on top. It decides by measuring the buttons, not by a screen-width guess, so it also stacks at
 * 200% font, on a 320dp screen and with long translations, and the decline action never gets
 * clipped or hidden.
 */
@Composable
fun ButtonPair(
    spacing: Dp,
    secondary: @Composable () -> Unit,
    primary: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(content = {
        secondary()
        primary()
    }, modifier = modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val (secondaryItem, primaryItem) = measurables
        val naturalWidths = measurables.sumOf { it.maxIntrinsicWidth(Constraints.Infinity) }
        val sideBySide = naturalWidths + gap <= constraints.maxWidth
        if (sideBySide) {
            val width = (constraints.maxWidth - gap) / 2
            val fixed = Constraints.fixedWidth(width)
            val left = secondaryItem.measure(fixed)
            val right = primaryItem.measure(fixed)
            val height = max(left.height, right.height)
            layout(constraints.maxWidth, height) {
                left.placeRelative(0, 0)
                right.placeRelative(width + gap, 0)
            }
        } else {
            val fixed = Constraints.fixedWidth(constraints.maxWidth)
            val top = primaryItem.measure(fixed)
            val bottom = secondaryItem.measure(fixed)
            layout(constraints.maxWidth, top.height + gap + bottom.height) {
                top.placeRelative(0, 0)
                bottom.placeRelative(0, top.height + gap)
            }
        }
    }
}
