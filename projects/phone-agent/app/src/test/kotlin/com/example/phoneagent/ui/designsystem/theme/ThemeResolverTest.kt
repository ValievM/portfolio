package com.example.phoneagent.ui.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeResolverTest {
    @Test
    fun `an explicit light choice stays light on a dark system`() {
        assertEquals(false, resolveTheme(useDarkTheme = false, systemDark = true, useDynamicColors = false, sdkInt = 34).dark)
    }

    @Test
    fun `an explicit dark choice stays dark on a light system`() {
        assertEquals(true, resolveTheme(useDarkTheme = true, systemDark = false, useDynamicColors = false, sdkInt = 34).dark)
    }

    @Test
    fun `the system choice follows a light system`() {
        assertEquals(false, resolveTheme(useDarkTheme = null, systemDark = false, useDynamicColors = false, sdkInt = 34).dark)
    }

    @Test
    fun `the system choice follows a dark system`() {
        assertEquals(true, resolveTheme(useDarkTheme = null, systemDark = true, useDynamicColors = false, sdkInt = 34).dark)
    }

    @Test
    fun `system colors are ignored before Android 12 but the saved choice is kept by the caller`() {
        assertEquals(false, resolveTheme(useDarkTheme = null, systemDark = false, useDynamicColors = true, sdkInt = 30).dynamicColor)
    }

    @Test
    fun `system colors apply from Android 12`() {
        assertEquals(true, resolveTheme(useDarkTheme = null, systemDark = false, useDynamicColors = true, sdkInt = 31).dynamicColor)
    }

    @Test
    fun `system colors stay off when the user did not ask for them`() {
        assertEquals(false, resolveTheme(useDarkTheme = null, systemDark = false, useDynamicColors = false, sdkInt = 34).dynamicColor)
    }

    @Test
    fun `choosing the system theme does not switch system colors on`() {
        assertEquals(false, resolveTheme(useDarkTheme = null, systemDark = true, useDynamicColors = false, sdkInt = 34).dynamicColor)
    }
}
