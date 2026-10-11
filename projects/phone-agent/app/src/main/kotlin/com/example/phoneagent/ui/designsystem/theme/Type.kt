package com.example.phoneagent.ui.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.phoneagent.R

// inter.ttf is a variable font: without an explicit `wght` value every entry below would render at
// the file's default weight, so SemiBold titles and **bold** text would look like Regular.
@OptIn(ExperimentalTextApi::class)
private fun inter(weight: FontWeight) =
    Font(R.font.inter, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

private val Inter =
    FontFamily(
        inter(FontWeight.Normal),
        inter(FontWeight.Medium),
        inter(FontWeight.SemiBold),
        inter(FontWeight.Bold),
    )

val AppMonospace: FontFamily = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))

private fun role(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
) = TextStyle(
    fontFamily = Inter,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = 0.sp,
)

/** The scale from the brand contract (docs/design/brand.md): every UI role has zero tracking. */
internal val AppTypography =
    Typography(
        displayLarge = role(57, 64, FontWeight.Normal),
        displayMedium = role(45, 52, FontWeight.Normal),
        displaySmall = role(36, 44, FontWeight.Normal),
        headlineLarge = role(32, 40, FontWeight.Medium),
        headlineMedium = role(28, 36, FontWeight.Medium),
        headlineSmall = role(24, 32, FontWeight.Medium),
        titleLarge = role(22, 28, FontWeight.SemiBold),
        titleMedium = role(16, 24, FontWeight.SemiBold),
        titleSmall = role(14, 20, FontWeight.SemiBold),
        bodyLarge = role(16, 24, FontWeight.Normal),
        bodyMedium = role(14, 20, FontWeight.Normal),
        bodySmall = role(12, 16, FontWeight.Normal),
        labelLarge = role(14, 20, FontWeight.SemiBold),
        labelMedium = role(12, 16, FontWeight.SemiBold),
        labelSmall = role(11, 16, FontWeight.SemiBold),
    )

/** Code and tool arguments: JetBrains Mono 14/22 with ligatures off, so `->` and `!=` read literally. */
val AppCodeTextStyle: TextStyle =
    TextStyle(
        fontFamily = AppMonospace,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "'liga' 0, 'calt' 0",
    )

/** A time of day (07:30): tabular numerals keep digits from shifting when the value changes. */
val AppTimeTextStyle: TextStyle = role(24, 30, FontWeight.SemiBold).copy(fontFeatureSettings = "'tnum'")
