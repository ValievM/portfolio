package com.example.phoneagent.ui.designsystem.theme

/** What the theme is actually drawn with, after the saved choice and the device are taken into account. */
internal data class ResolvedTheme(
    val dark: Boolean,
    val dynamicColor: Boolean,
)

private const val FIRST_DYNAMIC_COLOR_SDK = 31

/**
 * The saved [useDarkTheme] wins over the system; `null` means "follow the system". System (dynamic)
 * colors are a separate choice and only exist from Android 12: on older versions the saved value is
 * kept by the caller but not applied.
 */
internal fun resolveTheme(
    useDarkTheme: Boolean?,
    systemDark: Boolean,
    useDynamicColors: Boolean,
    sdkInt: Int,
): ResolvedTheme =
    ResolvedTheme(
        dark = useDarkTheme ?: systemDark,
        dynamicColor = useDynamicColors && sdkInt >= FIRST_DYNAMIC_COLOR_SDK,
    )
