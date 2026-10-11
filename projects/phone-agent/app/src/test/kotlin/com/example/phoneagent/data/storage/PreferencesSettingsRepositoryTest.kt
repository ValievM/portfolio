package com.example.phoneagent.data.storage

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.example.phoneagent.domain.storage.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreferencesSettingsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `missing preferences load default settings including no model`() =
        runTest {
            val settings = repository(backgroundScope).load()

            assertEquals(AppSettings(model = null), settings)
        }

    @Test
    fun `saved settings including model are loaded back`() =
        runTest {
            val repository = repository(backgroundScope)
            val settings = AppSettings(useDynamicColors = false, useDarkTheme = true, model = "gpt-5.6-luna")

            repository.save(settings)

            assertEquals(settings, repository.load())
        }

    @Test
    fun `saving null model clears the previously saved model`() =
        runTest {
            val repository = repository(backgroundScope)
            repository.save(AppSettings(model = "gpt-5.6-luna"))

            repository.save(AppSettings(model = null))

            assertEquals(null, repository.load().model)
        }

    @Test
    fun `saved search folder uri is loaded back`() =
        runTest {
            val repository = repository(backgroundScope)
            val settings = AppSettings(searchFolderUri = "content://example/tree/folder")

            repository.save(settings)

            assertEquals(settings, repository.load())
        }

    @Test
    fun `system colors are off by default so users see the brand palette`() =
        runTest {
            assertEquals(false, repository(backgroundScope).load().useDynamicColors)
        }

    @Test
    fun `a dynamic colors value stored before the brand theme does not switch system colors on`() =
        runTest {
            val dataStore = dataStore(backgroundScope)
            dataStore.edit { it[booleanPreferencesKey("use_dynamic_colors")] = true }

            assertEquals(false, PreferencesSettingsRepository(dataStore).load().useDynamicColors)
        }

    @Test
    fun `observe emits the current settings and every later save`() =
        runTest {
            val repository = repository(backgroundScope)
            val seen = mutableListOf<AppSettings>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.observe().collect(seen::add) }

            repository.save(AppSettings(useDarkTheme = true))
            repository.save(AppSettings(useDarkTheme = false, useDynamicColors = true))
            advanceUntilIdle()

            assertEquals(
                listOf(AppSettings(), AppSettings(useDarkTheme = true), AppSettings(useDarkTheme = false, useDynamicColors = true)),
                seen,
            )
        }

    private fun dataStore(scope: CoroutineScope) =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { temporaryFolder.newFile("settings-${System.nanoTime()}.preferences_pb") },
        )

    private fun repository(scope: CoroutineScope): PreferencesSettingsRepository {
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { temporaryFolder.newFile("settings-${System.nanoTime()}.preferences_pb") },
            )
        return PreferencesSettingsRepository(dataStore)
    }
}
