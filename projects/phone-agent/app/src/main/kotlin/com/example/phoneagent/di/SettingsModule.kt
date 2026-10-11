package com.example.phoneagent.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.phoneagent.data.storage.PreferencesSettingsRepository
import com.example.phoneagent.domain.storage.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val APP_SETTINGS_DATA_STORE_NAME = "app_settings"

private val Context.appSettingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = APP_SETTINGS_DATA_STORE_NAME)

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SettingsBindingsModule {
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(implementation: PreferencesSettingsRepository): SettingsRepository
}

@Module
@InstallIn(SingletonComponent::class)
internal object SettingsDataStoreModule {
    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.applicationContext.appSettingsDataStore
}
