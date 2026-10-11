package com.example.phoneagent.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.phoneagent.auth.AppAuthStatus
import com.example.phoneagent.auth.AuthCoordinator
import com.example.phoneagent.auth.toAppSignInMethod
import com.example.phoneagent.domain.model.ModelCatalog
import com.example.phoneagent.domain.model.ModelCatalogResult
import com.example.phoneagent.domain.storage.AppSettings
import com.example.phoneagent.domain.storage.ConversationRepository
import com.example.phoneagent.domain.storage.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val authCoordinator: AuthCoordinator,
        private val settingsRepository: SettingsRepository,
        private val modelCatalog: ModelCatalog,
        private val conversationRepository: ConversationRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(SettingsUiState())
        private val settingsMutex = Mutex()
        private var currentSettings = AppSettings()

        val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

        init {
            viewModelScope.launch {
                authCoordinator.state.collect { status ->
                    mutableState.update { state -> state.withAuthStatus(status) }
                }
            }
            viewModelScope.launch {
                when (val result = loadModelCatalog()) {
                    is ModelCatalogResult.Available -> {
                        mutableState.update {
                            it.copy(availableModels = result.models, modelCatalogUnavailable = false, isLoadingModels = false)
                        }
                    }

                    ModelCatalogResult.Unavailable -> {
                        mutableState.update { it.copy(modelCatalogUnavailable = true, isLoadingModels = false) }
                    }
                }
            }
            viewModelScope.launch {
                try {
                    currentSettings = settingsRepository.load()
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            model = currentSettings.model,
                            themeMode = currentSettings.toThemeMode(),
                            useSystemColors = currentSettings.useDynamicColors,
                            searchFolderSelected = currentSettings.searchFolderUri != null,
                            settingsUnavailable = false,
                        )
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    mutableState.update { it.copy(isLoading = false, settingsUnavailable = true, settingsLoadFailed = true) }
                }
            }
        }

        fun onEvent(event: SettingsUiEvent) {
            when (event) {
                is SettingsUiEvent.SelectModel -> {
                    selectModel(event.model)
                }

                is SettingsUiEvent.SelectThemeMode -> {
                    updateSettings(onFailure = { it.copy(themeSaveFailed = true) }) { it.copy(useDarkTheme = event.mode.toDarkThemeFlag()) }
                }

                is SettingsUiEvent.SetSystemColors -> {
                    updateSettings(onFailure = { it.copy(themeSaveFailed = true) }) { it.copy(useDynamicColors = event.enabled) }
                }

                SettingsUiEvent.SignOut -> {
                    signOut()
                }

                is SettingsUiEvent.SaveSearchFolder -> {
                    saveSearchFolder(event.uri)
                }

                SettingsUiEvent.ClearHistory -> {
                    clearHistory()
                }

                SettingsUiEvent.SearchFolderPermissionUnavailable -> {
                    mutableState.update { it.copy(folderPermissionUnavailable = true) }
                }
            }
        }

        private fun signOut() {
            viewModelScope.launch {
                try {
                    authCoordinator.signOut()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    mutableState.update { it.copy(settingsUnavailable = true) }
                }
            }
        }

        private fun selectModel(model: String?) = updateSettings { it.copy(model = model) }

        private fun saveSearchFolder(uri: String?) = updateSettings { it.copy(searchFolderUri = uri) }

        /**
         * The only writer of the settings. Nothing is written until they have been read successfully:
         * saving on top of defaults would erase the saved model and folder. [onFailure] says which
         * error the screen shows when this particular save fails.
         */
        private fun updateSettings(
            onFailure: (SettingsUiState) -> SettingsUiState = { it.copy(settingsUnavailable = true) },
            transform: (AppSettings) -> AppSettings,
        ) {
            if (!mutableState.value.canEditSettings) return
            viewModelScope.launch {
                settingsMutex.withLock {
                    val updatedSettings = transform(currentSettings)
                    if (updatedSettings == currentSettings) return@withLock
                    try {
                        settingsRepository.save(updatedSettings)
                        currentSettings = updatedSettings
                        mutableState.update {
                            it.copy(
                                model = updatedSettings.model,
                                themeMode = updatedSettings.toThemeMode(),
                                useSystemColors = updatedSettings.useDynamicColors,
                                searchFolderSelected = updatedSettings.searchFolderUri != null,
                                folderPermissionUnavailable = false,
                                settingsUnavailable = false,
                                themeSaveFailed = false,
                            )
                        }
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        mutableState.update(onFailure)
                    }
                }
            }
        }

        private fun clearHistory() {
            viewModelScope.launch {
                try {
                    conversationRepository.replace(emptyList())
                    mutableState.update { it.copy(historyClearUnavailable = false) }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    mutableState.update { it.copy(historyClearUnavailable = true) }
                }
            }
        }

        private suspend fun loadModelCatalog(): ModelCatalogResult =
            try {
                modelCatalog.load()
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                ModelCatalogResult.Unavailable
            }

        private fun SettingsUiState.withAuthStatus(status: AppAuthStatus): SettingsUiState =
            when (status) {
                AppAuthStatus.Restoring,
                AppAuthStatus.SigningIn,
                is AppAuthStatus.DeviceCode,
                is AppAuthStatus.Failure,
                -> {
                    copy(isSignedIn = false, sessionExpired = false, signInMethod = null)
                }

                is AppAuthStatus.SignedOut -> {
                    copy(isSignedIn = false, sessionExpired = status.sessionExpired, signInMethod = null)
                }

                is AppAuthStatus.SignedIn -> {
                    copy(isSignedIn = true, sessionExpired = false, signInMethod = status.method.toAppSignInMethod())
                }
            }
    }
