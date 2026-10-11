package com.example.phoneagent.ui.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

internal val AppShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

/**
 * Radii of the five surfaces the brand contract names. They do not map onto one Material role each,
 * so components read them from here instead of picking `MaterialTheme.shapes.large` by accident.
 * The radii stay the same at any font scale: containers grow, the geometry does not.
 */
@Immutable
data class AppShapeTokens(
    /** The user's message: round, with a small 4dp "tail" in the bottom end corner. */
    val userBubble: RoundedCornerShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 4.dp, bottomStart = 20.dp),
    /** Tool cards, settings groups, banners and other 16dp containers. */
    val card: RoundedCornerShape = RoundedCornerShape(16.dp),
    val sheet: RoundedCornerShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    val button: RoundedCornerShape = RoundedCornerShape(16.dp),
    val composer: RoundedCornerShape = RoundedCornerShape(24.dp),
)
