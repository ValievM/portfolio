package com.example.phoneagent.screenshot

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage

/** Theme/scale combinations every component is captured in (docs/design/qa.md "Design QA"). */
internal enum class Variant(
    val suffix: String,
    val darkTheme: Boolean,
    val fontScale: Float,
) {
    Light("light", darkTheme = false, fontScale = 1f),
    Dark("dark", darkTheme = true, fontScale = 1f),
    LargeFont("light_font200", darkTheme = false, fontScale = 2f),
}

private const val SCREENSHOT_DIR = "src/test/screenshots"

/**
 * Renders [content] inside the app theme and captures it as `<name>_<variant>.png` for each
 * variant. Without the Roborazzi record/verify flags the capture is skipped, so this doubles as a
 * smoke test that the composable renders at all in every variant (recording baselines:
 * docs/design/qa.md). Content is set once and the variant is switched through state, because a
 * compose test rule accepts a single `setContent` per test.
 */
@OptIn(ExperimentalRoborazziApi::class)
internal fun ComposeContentTestRule.snapshot(
    name: String,
    variants: List<Variant> = listOf(Variant.Light, Variant.Dark),
    fillScreen: Boolean = false,
    wholeScreen: Boolean = false,
    immediateWork: Boolean = false,
    content: @Composable () -> Unit,
) {
    var current by mutableStateOf(variants.first())
    setContent {
        val variant = current
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, variant.fontScale)) {
            AppTheme(darkTheme = variant.darkTheme, useDynamicColor = false) {
                Surface(modifier = if (fillScreen) Modifier.fillMaxSize() else Modifier) {
                    // Inspection mode makes the markdown renderer parse synchronously, so the
                    // capture never sees its "loading" placeholder.
                    CompositionLocalProvider(LocalInspectionMode provides immediateWork) {
                        content()
                    }
                }
            }
        }
    }
    variants.forEach { variant ->
        runOnIdle { current = variant }
        waitForIdle()
        val path = "$SCREENSHOT_DIR/${name}_${variant.suffix}.png"
        if (wholeScreen) captureScreenRoboImage(path) else onRoot().captureRoboImage(path)
    }
}
