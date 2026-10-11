package com.example.phoneagent.ui.designsystem.components

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.theme.AppMotion
import com.example.phoneagent.ui.designsystem.theme.AppTheme

// The two parts of the "Стык" mark on its original 64 x 64 grid (the same paths as ic_brand_mark.xml).
private const val MARK_GRID = 64f
private const val LEFT_PART = "M10 14H26V24H20V42H32V52H10Z"
private const val RIGHT_PART = "M38 12H54V50H44V32H32V22H38Z"
private val FRAME = 24.dp
private val PART_TRAVEL = 1.dp

/**
 * "Готовлю ответ": the mark in a 24dp frame plus a label, shown only while the model is preparing or
 * writing its text. The two parts move 1dp apart and back over 1200ms (never toward each other, so the
 * gap never closes). With [animated] false, and always in previews and when the system animation scale
 * is 0, the parts stand still at their original position instead of running a sped-up cycle.
 */
@Composable
fun PreparingIndicator(
    modifier: Modifier = Modifier,
    animated: Boolean = !rememberReducedMotion(),
) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
    ) {
        val spread = rememberSpread(animated && !LocalInspectionMode.current)
        // Read inside the draw scope: the animation redraws the 24dp canvas only, it never recomposes.
        SteppedMark(tint = AppTheme.extendedColors.brandMark, spread = { spread.value })
        Text(
            text = stringResource(R.string.model_working),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** True when the user turned system animations off (animator duration scale 0). */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

@Composable
private fun rememberSpread(animated: Boolean): State<Float> {
    if (!animated) return remember { mutableFloatStateOf(0f) }
    return rememberInfiniteTransition(label = "preparing").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(AppMotion.PREPARING_HALF_CYCLE_MILLIS, easing = AppMotion.Preparing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "spread",
    )
}

@Composable
private fun SteppedMark(
    tint: Color,
    spread: () -> Float,
) {
    val parser = remember { PathParser() }
    val left = remember { parser.parsePathString(LEFT_PART).toPath() }
    val right = remember { parser.parsePathString(RIGHT_PART).toPath() }
    Canvas(modifier = Modifier.size(FRAME)) {
        val travel = PART_TRAVEL.toPx() * spread()
        drawPart(left, tint, offsetX = -travel)
        drawPart(right, tint, offsetX = travel)
    }
}

private fun DrawScope.drawPart(
    part: Path,
    tint: Color,
    offsetX: Float,
) {
    translate(left = offsetX) {
        scale(scale = size.width / MARK_GRID, pivot = Offset.Zero) { drawPath(part, tint) }
    }
}
