package com.example.phoneagent.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.example.phoneagent.ui.chat.ChatRoute
import com.example.phoneagent.ui.settings.SettingsRoute

sealed interface AppDestination {
    data object Chat : AppDestination

    data object Settings : AppDestination
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val backStack = remember { mutableStateListOf<AppDestination>(AppDestination.Chat) }
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = { key ->
            when (key) {
                AppDestination.Chat -> {
                    NavEntry(key) {
                        ChatRoute(onOpenSettings = { backStack += AppDestination.Settings })
                    }
                }

                AppDestination.Settings -> {
                    NavEntry(key) {
                        SettingsRoute(onBack = { backStack.removeLastOrNull() })
                    }
                }
            }
        },
    )
}
