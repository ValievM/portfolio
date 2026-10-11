package com.example.phoneagent.ui.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class AppSpacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    /** The 12dp step between `small` and `medium`, for tight groups such as list rows. */
    val compact: Dp = 12.dp,
)

@Immutable
data class AppExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    /** Tint of the brand mark inside the UI: the brand color, or `onSurface` when system colors are on. */
    val brandMark: Color,
)

private val LocalSpacing = staticCompositionLocalOf { AppSpacing() }
private val LocalExtendedColors = staticCompositionLocalOf { BrandLightExtendedColors }

object AppTheme {
    val spacing: AppSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalSpacing.current

    val extendedColors: AppExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtendedColors.current

    /** Radii do not depend on the color scheme or the font scale, so this is a plain constant, not a CompositionLocal. */
    val shapeTokens: AppShapeTokens = AppShapeTokens()
}

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dynamic = useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val targetScheme =
        when {
            dynamic && darkTheme -> dynamicDarkColorScheme(context)
            dynamic -> dynamicLightColorScheme(context)
            darkTheme -> BrandDarkColorScheme
            else -> BrandLightColorScheme
        }
    val colorScheme = targetScheme.animated()
    val brandExtended = if (darkTheme) BrandDarkExtendedColors else BrandLightExtendedColors
    val extendedColors = (if (dynamic) brandExtended.copy(brandMark = targetScheme.onSurface) else brandExtended).animated()

    androidx.compose.runtime.CompositionLocalProvider(
        LocalSpacing provides AppSpacing(),
        LocalExtendedColors provides extendedColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
