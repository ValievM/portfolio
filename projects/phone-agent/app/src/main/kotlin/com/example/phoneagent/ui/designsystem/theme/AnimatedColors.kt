package com.example.phoneagent.ui.designsystem.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** A theme switch cross-fades over this long instead of flipping in one frame. */
internal const val THEME_FADE_MILLIS = 250

// The first composition takes the target value as is (nothing animates on start); only a later change
// of the target fades. Compose animations follow the system "remove animations" setting on their own,
// so with animator scale 0 the change is instant. Only readers of the animated colors recompose.
@Composable
private fun Color.fade(): Color = animateColorAsState(this, tween(THEME_FADE_MILLIS), label = "theme").value

/** The same scheme with every role that the UI reads animated; the seldom used "fixed" roles keep their value. */
@Composable
internal fun ColorScheme.animated(): ColorScheme =
    copy(
        primary = primary.fade(),
        onPrimary = onPrimary.fade(),
        primaryContainer = primaryContainer.fade(),
        onPrimaryContainer = onPrimaryContainer.fade(),
        inversePrimary = inversePrimary.fade(),
        secondary = secondary.fade(),
        onSecondary = onSecondary.fade(),
        secondaryContainer = secondaryContainer.fade(),
        onSecondaryContainer = onSecondaryContainer.fade(),
        tertiary = tertiary.fade(),
        onTertiary = onTertiary.fade(),
        tertiaryContainer = tertiaryContainer.fade(),
        onTertiaryContainer = onTertiaryContainer.fade(),
        background = background.fade(),
        onBackground = onBackground.fade(),
        surface = surface.fade(),
        onSurface = onSurface.fade(),
        surfaceVariant = surfaceVariant.fade(),
        onSurfaceVariant = onSurfaceVariant.fade(),
        surfaceTint = surfaceTint.fade(),
        inverseSurface = inverseSurface.fade(),
        inverseOnSurface = inverseOnSurface.fade(),
        error = error.fade(),
        onError = onError.fade(),
        errorContainer = errorContainer.fade(),
        onErrorContainer = onErrorContainer.fade(),
        outline = outline.fade(),
        outlineVariant = outlineVariant.fade(),
        scrim = scrim.fade(),
        surfaceBright = surfaceBright.fade(),
        surfaceDim = surfaceDim.fade(),
        surfaceContainer = surfaceContainer.fade(),
        surfaceContainerHigh = surfaceContainerHigh.fade(),
        surfaceContainerHighest = surfaceContainerHighest.fade(),
        surfaceContainerLow = surfaceContainerLow.fade(),
        surfaceContainerLowest = surfaceContainerLowest.fade(),
    )

@Composable
internal fun AppExtendedColors.animated(): AppExtendedColors =
    AppExtendedColors(
        success = success.fade(),
        onSuccess = onSuccess.fade(),
        successContainer = successContainer.fade(),
        onSuccessContainer = onSuccessContainer.fade(),
        warning = warning.fade(),
        onWarning = onWarning.fade(),
        warningContainer = warningContainer.fade(),
        onWarningContainer = onWarningContainer.fade(),
        brandMark = brandMark.fade(),
    )
