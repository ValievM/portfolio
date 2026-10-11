package com.example.phoneagent.data.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.phoneagent.domain.storage.AppSettings
import com.example.phoneagent.domain.storage.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

public class PreferencesSettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SettingsRepository {
        override suspend fun load(): AppSettings = observe().first()

        override fun observe(): Flow<AppSettings> =
            dataStore.data.map { preferences ->
                AppSettings(
                    useDynamicColors = preferences[USE_SYSTEM_COLORS] ?: false,
                    useDarkTheme = preferences[USE_DARK_THEME],
                    model = preferences[MODEL],
                    searchFolderUri = preferences[SEARCH_FOLDER_URI],
                )
            }

        override suspend fun save(settings: AppSettings) {
            val useDarkTheme = settings.useDarkTheme
            val model = settings.model
            val searchFolderUri = settings.searchFolderUri
            dataStore.edit { preferences ->
                preferences[USE_SYSTEM_COLORS] = settings.useDynamicColors
                if (useDarkTheme == null) {
                    preferences.remove(USE_DARK_THEME)
                } else {
                    preferences[USE_DARK_THEME] = useDarkTheme
                }
                if (model == null) {
                    preferences.remove(MODEL)
                } else {
                    preferences[MODEL] = model
                }
                if (searchFolderUri == null) {
                    preferences.remove(SEARCH_FOLDER_URI)
                } else {
                    preferences[SEARCH_FOLDER_URI] = searchFolderUri
                }
            }
        }

        private companion object {
            // A new key on purpose: "use_dynamic_colors" defaulted to true before the brand theme, and every
            // settings save wrote it, so reusing it would keep the wallpaper palette on for existing installs.
            val USE_SYSTEM_COLORS = booleanPreferencesKey("use_system_colors")
            val USE_DARK_THEME = booleanPreferencesKey("use_dark_theme")
            val MODEL = stringPreferencesKey("model")
            val SEARCH_FOLDER_URI = stringPreferencesKey("search_folder_uri")
        }
    }
