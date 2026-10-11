package com.example.phoneagent.domain.storage

import com.example.phoneagent.domain.conversation.HistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

public interface ConversationRepository {
    public suspend fun load(): List<HistoryItem>

    public suspend fun replace(items: List<HistoryItem>)

    public fun observe(): Flow<List<HistoryItem>>
}

public data class AppSettings(
    /** "System colors" (Android 12+ wallpaper palette). Off by default: the brand palette is what users see. */
    public val useDynamicColors: Boolean = false,
    public val useDarkTheme: Boolean? = null,
    public val model: String? = null,
    public val searchFolderUri: String? = null,
)

public interface SettingsRepository {
    public suspend fun load(): AppSettings

    /** Emits the current settings and every later change; the default emits once (for fakes and one-shot implementations). */
    public fun observe(): Flow<AppSettings> = flow { emit(load()) }

    public suspend fun save(settings: AppSettings)
}
