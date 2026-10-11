package com.example.phoneagent.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.phoneagent.domain.storage.AppSettings
import com.example.phoneagent.domain.storage.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the app root knows about the saved settings, and so about which theme to draw. */
sealed interface ThemeState {
    /** Nothing has been read yet: the splash screen is still up. */
    data object Loading : ThemeState

    data class Ready(
        val settings: AppSettings,
    ) : ThemeState

    /** Reading failed. [lastKnown] is the last good value (null when it failed on the very first read). */
    data class Unavailable(
        val lastKnown: AppSettings?,
    ) : ThemeState
}

/** The settings to draw the theme with, or null while there is nothing to draw it with yet. */
val ThemeState.settings: AppSettings?
    get() =
        when (this) {
            ThemeState.Loading -> null
            is ThemeState.Ready -> settings
            is ThemeState.Unavailable -> lastKnown
        }

/**
 * The single source of the app-wide theme: it observes the saved settings, so a change made on the
 * settings screen reaches the whole app at once and nothing is recreated. A failed read is never
 * turned into default settings and nothing is written back; [retry] subscribes again on demand,
 * there is no automatic retry loop.
 */
@HiltViewModel
class ThemeViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow<ThemeState>(ThemeState.Loading)
        private var subscription: Job? = null

        val state: StateFlow<ThemeState> = mutableState.asStateFlow()

        init {
            subscribe()
        }

        fun retry() {
            if (mutableState.value is ThemeState.Unavailable) subscribe()
        }

        private fun subscribe() {
            subscription?.cancel()
            subscription =
                viewModelScope.launch {
                    try {
                        settingsRepository.observe().collect { settings -> mutableState.value = ThemeState.Ready(settings) }
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        mutableState.update { ThemeState.Unavailable(lastKnown = it.settings) }
                    }
                }
        }
    }
