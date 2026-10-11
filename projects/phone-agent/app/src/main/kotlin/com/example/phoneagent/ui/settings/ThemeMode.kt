package com.example.phoneagent.ui.settings

import com.example.phoneagent.domain.storage.AppSettings

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

internal fun AppSettings.toThemeMode(): ThemeMode =
    when (useDarkTheme) {
        null -> ThemeMode.SYSTEM
        false -> ThemeMode.LIGHT
        true -> ThemeMode.DARK
    }

internal fun ThemeMode.toDarkThemeFlag(): Boolean? =
    when (this) {
        ThemeMode.SYSTEM -> null
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
