package com.example.phoneagent.ui.designsystem.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Numbers come from docs/design/brand.md (the "Стык" contract): radii, the type scale, code and time. */
class ThemeGeometryTest {
    private val density = Density(1f)
    private val box = Size(400f, 400f)

    private fun CornerBasedShape.radii(): List<Float> = listOf(topStart, topEnd, bottomEnd, bottomStart).map { it.toPx(box, density) }

    @Test
    fun `the user bubble is round with a small tail in the bottom end corner`() {
        assertEquals(listOf(20f, 20f, 4f, 20f), AppShapeTokens().userBubble.radii())
    }

    @Test
    fun `the card is 16dp on every corner`() {
        assertEquals(listOf(16f, 16f, 16f, 16f), AppShapeTokens().card.radii())
    }

    @Test
    fun `the sheet is rounded only on top by 28dp`() {
        assertEquals(listOf(28f, 28f, 0f, 0f), AppShapeTokens().sheet.radii())
    }

    @Test
    fun `the button is 16dp and the composer 24dp`() {
        assertEquals(listOf(16f, 16f, 16f, 16f), AppShapeTokens().button.radii())
        assertEquals(listOf(24f, 24f, 24f, 24f), AppShapeTokens().composer.radii())
    }

    @Test
    fun `body text keeps 16 14 and 12 sizes at regular weight`() {
        assertRole(AppTypography.bodyLarge, 16, 24, FontWeight.Normal)
        assertRole(AppTypography.bodyMedium, 14, 20, FontWeight.Normal)
        assertRole(AppTypography.bodySmall, 12, 16, FontWeight.Normal)
    }

    @Test
    fun `titles use semibold`() {
        assertRole(AppTypography.titleLarge, 22, 28, FontWeight.SemiBold)
        assertRole(AppTypography.titleMedium, 16, 24, FontWeight.SemiBold)
        assertRole(AppTypography.titleSmall, 14, 20, FontWeight.SemiBold)
    }

    @Test
    fun `buttons and labels use semibold`() {
        assertRole(AppTypography.labelLarge, 14, 20, FontWeight.SemiBold)
        assertRole(AppTypography.labelMedium, 12, 16, FontWeight.SemiBold)
        assertRole(AppTypography.labelSmall, 11, 16, FontWeight.SemiBold)
    }

    @Test
    fun `headlines are medium and displays are regular`() {
        assertRole(AppTypography.headlineLarge, 32, 40, FontWeight.Medium)
        assertRole(AppTypography.headlineMedium, 28, 36, FontWeight.Medium)
        assertRole(AppTypography.headlineSmall, 24, 32, FontWeight.Medium)
        assertRole(AppTypography.displayLarge, 57, 64, FontWeight.Normal)
        assertRole(AppTypography.displayMedium, 45, 52, FontWeight.Normal)
        assertRole(AppTypography.displaySmall, 36, 44, FontWeight.Normal)
    }

    @Test
    fun `no role adds letter spacing`() {
        val roles =
            listOf(
                AppTypography.bodyLarge,
                AppTypography.titleLarge,
                AppTypography.labelLarge,
                AppTypography.labelSmall,
                AppTypography.headlineSmall,
            )
        assertTrue(roles.all { it.letterSpacing == 0.sp })
    }

    @Test
    fun `code is monospace 14 over 22 with ligatures off`() {
        assertEquals(AppMonospace, AppCodeTextStyle.fontFamily)
        assertEquals(14.sp, AppCodeTextStyle.fontSize)
        assertEquals(22.sp, AppCodeTextStyle.lineHeight)
        val features = AppCodeTextStyle.fontFeatureSettings.orEmpty()
        assertTrue("ligatures are switched off: $features", "liga" in features && "calt" in features)
    }

    @Test
    fun `time is 24 over 30 semibold with tabular numerals`() {
        assertEquals(24.sp, AppTimeTextStyle.fontSize)
        assertEquals(30.sp, AppTimeTextStyle.lineHeight)
        assertEquals(FontWeight.SemiBold, AppTimeTextStyle.fontWeight)
        val features = AppTimeTextStyle.fontFeatureSettings.orEmpty()
        assertTrue("tabular numerals: $features", "tnum" in features)
    }

    @Test
    fun `the spacing scale has the named 12dp step`() {
        assertEquals(12f, AppSpacing().compact.value, 0f)
    }

    private fun assertRole(
        style: TextStyle,
        size: Int,
        lineHeight: Int,
        weight: FontWeight,
    ) {
        assertEquals(size.sp, style.fontSize)
        assertEquals(lineHeight.sp, style.lineHeight)
        assertEquals(weight, style.fontWeight)
    }
}
