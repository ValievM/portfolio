package com.example.phoneagent.ui.settings

import com.example.phoneagent.auth.AppAuthStatus
import com.example.phoneagent.auth.AppSignInMethod
import com.example.phoneagent.auth.AuthCoordinator
import com.example.phoneagent.data.auth.AuthMethod
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.model.ModelCatalog
import com.example.phoneagent.domain.model.ModelCatalogResult
import com.example.phoneagent.domain.storage.AppSettings
import com.example.phoneagent.domain.storage.ConversationRepository
import com.example.phoneagent.domain.storage.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `selecting model saves it through settings repository`() =
        runTest(dispatcher.scheduler) {
            val initialSettings =
                AppSettings(
                    useDynamicColors = false,
                    useDarkTheme = true,
                    model = "gpt-5.6-luna",
                    searchFolderUri = "content://example/tree/current",
                )
            val settingsRepository = RecordingSettingsRepository(initialSettings)
            val viewModel =
                SettingsViewModel(
                    authCoordinator = FakeAuthCoordinator(),
                    settingsRepository = settingsRepository,
                    modelCatalog = FakeModelCatalog(),
                    conversationRepository = FakeConversationRepository(),
                )
            advanceUntilIdle()

            viewModel.onEvent(SettingsUiEvent.SelectModel("gpt-5.6-terra"))
            advanceUntilIdle()

            assertEquals(listOf(initialSettings.copy(model = "gpt-5.6-terra")), settingsRepository.savedSettings)
        }

    @Test
    fun `signed in state exposes the authentication method`() =
        runTest(dispatcher.scheduler) {
            val viewModel =
                SettingsViewModel(
                    authCoordinator = FakeAuthCoordinator(AppAuthStatus.SignedIn(AuthMethod.API_KEY)),
                    settingsRepository = RecordingSettingsRepository(AppSettings()),
                    modelCatalog = FakeModelCatalog(),
                    conversationRepository = FakeConversationRepository(),
                )
            advanceUntilIdle()

            assertEquals(AppSignInMethod.ApiKey, viewModel.state.value.signInMethod)
        }

    @Test
    fun `unavailable model catalog is exposed without clearing saved model`() =
        runTest(dispatcher.scheduler) {
            val savedModel = "gpt-5.6-luna"
            val viewModel =
                SettingsViewModel(
                    authCoordinator = FakeAuthCoordinator(),
                    settingsRepository = RecordingSettingsRepository(AppSettings(model = savedModel)),
                    modelCatalog = FakeModelCatalog(ModelCatalogResult.Unavailable),
                    conversationRepository = FakeConversationRepository(),
                )
            advanceUntilIdle()

            assertTrue(viewModel.state.value.modelCatalogUnavailable)
            assertFalse(
                viewModel.state.value.availableModels
                    .isNotEmpty(),
            )
            assertEquals(savedModel, viewModel.state.value.model)
        }

    @Test
    fun `sign out delegates to the authentication coordinator`() =
        runTest(dispatcher.scheduler) {
            val authCoordinator = FakeAuthCoordinator(AppAuthStatus.SignedIn(AuthMethod.API_KEY))
            val viewModel =
                SettingsViewModel(
                    authCoordinator = authCoordinator,
                    settingsRepository = RecordingSettingsRepository(AppSettings()),
                    modelCatalog = FakeModelCatalog(),
                    conversationRepository = FakeConversationRepository(),
                )
            advanceUntilIdle()

            viewModel.onEvent(SettingsUiEvent.SignOut)
            advanceUntilIdle()

            assertEquals(1, authCoordinator.signOutCalls)
        }

    @Test
    fun `selecting a search folder persists its uri and preserves other settings`() =
        runTest(dispatcher.scheduler) {
            val initialSettings = AppSettings(model = "gpt-5.6-luna")
            val settingsRepository = RecordingSettingsRepository(initialSettings)
            val viewModel =
                SettingsViewModel(
                    authCoordinator = FakeAuthCoordinator(),
                    settingsRepository = settingsRepository,
                    modelCatalog = FakeModelCatalog(),
                    conversationRepository = FakeConversationRepository(),
                )
            advanceUntilIdle()

            viewModel.onEvent(SettingsUiEvent.SaveSearchFolder("content://example/tree/selected"))
            advanceUntilIdle()

            assertEquals(
                listOf(initialSettings.copy(searchFolderUri = "content://example/tree/selected")),
                settingsRepository.savedSettings,
            )
        }

    @Test
    fun `clear history replaces the persisted snapshot with an empty list`() =
        runTest(dispatcher.scheduler) {
            val conversationRepository = FakeConversationRepository()
            val viewModel =
                SettingsViewModel(
                    authCoordinator = FakeAuthCoordinator(),
                    settingsRepository = RecordingSettingsRepository(AppSettings()),
                    modelCatalog = FakeModelCatalog(),
                    conversationRepository = conversationRepository,
                )
            advanceUntilIdle()

            viewModel.onEvent(SettingsUiEvent.ClearHistory)
            advanceUntilIdle()

            assertEquals(listOf(emptyList<HistoryItem>()), conversationRepository.replacements)
        }

    @Test
    fun `the stored theme choices are exposed once settings are loaded`() =
        runTest(dispatcher.scheduler) {
            val viewModel = viewModelWith(RecordingSettingsRepository(AppSettings(useDarkTheme = false, useDynamicColors = true)))
            advanceUntilIdle()

            assertEquals(ThemeMode.LIGHT, viewModel.state.value.themeMode)
            assertEquals(true, viewModel.state.value.useSystemColors)
        }

    @Test
    fun `choosing a theme mode saves the matching dark theme flag and updates the state`() =
        runTest(dispatcher.scheduler) {
            val repository = RecordingSettingsRepository(AppSettings())
            val viewModel = viewModelWith(repository)
            advanceUntilIdle()

            viewModel.onEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.DARK))
            advanceUntilIdle()
            viewModel.onEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.LIGHT))
            advanceUntilIdle()
            viewModel.onEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.SYSTEM))
            advanceUntilIdle()

            assertEquals(listOf(true, false, null), repository.savedSettings.map { it.useDarkTheme })
            assertEquals(ThemeMode.SYSTEM, viewModel.state.value.themeMode)
        }

    @Test
    fun `switching system colors saves the choice and updates the state`() =
        runTest(dispatcher.scheduler) {
            val repository = RecordingSettingsRepository(AppSettings())
            val viewModel = viewModelWith(repository)
            advanceUntilIdle()

            viewModel.onEvent(SettingsUiEvent.SetSystemColors(true))
            advanceUntilIdle()

            assertEquals(listOf(true), repository.savedSettings.map { it.useDynamicColors })
            assertEquals(true, viewModel.state.value.useSystemColors)
        }

    @Test
    fun `a failed theme save keeps the previous choice and reports it`() =
        runTest(dispatcher.scheduler) {
            val repository = RecordingSettingsRepository(AppSettings(useDarkTheme = true))
            val viewModel = viewModelWith(repository)
            advanceUntilIdle()
            repository.failSaves = true

            viewModel.onEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.LIGHT))
            advanceUntilIdle()

            assertEquals(ThemeMode.DARK, viewModel.state.value.themeMode)
            assertTrue(viewModel.state.value.themeSaveFailed)
        }

    @Test
    fun `a failed model save is not reported as a theme error`() =
        runTest(dispatcher.scheduler) {
            val repository = RecordingSettingsRepository(AppSettings())
            val viewModel = viewModelWith(repository)
            advanceUntilIdle()
            repository.failSaves = true

            viewModel.onEvent(SettingsUiEvent.SelectModel("gpt-5.6-terra"))
            advanceUntilIdle()

            assertFalse(viewModel.state.value.themeSaveFailed)
            assertTrue(viewModel.state.value.settingsUnavailable)
        }

    @Test
    fun `a later successful save clears the theme error`() =
        runTest(dispatcher.scheduler) {
            val repository = RecordingSettingsRepository(AppSettings())
            val viewModel = viewModelWith(repository)
            advanceUntilIdle()
            repository.failSaves = true
            viewModel.onEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.DARK))
            advanceUntilIdle()

            repository.failSaves = false
            viewModel.onEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.DARK))
            advanceUntilIdle()

            assertFalse(viewModel.state.value.themeSaveFailed)
            assertEquals(ThemeMode.DARK, viewModel.state.value.themeMode)
        }

    @Test
    fun `a failed save does not change what the next save is based on`() =
        runTest(dispatcher.scheduler) {
            val repository = RecordingSettingsRepository(AppSettings(useDarkTheme = null, model = "gpt-5.6-luna"))
            val viewModel = viewModelWith(repository)
            advanceUntilIdle()
            repository.failSaves = true
            viewModel.onEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.DARK))
            advanceUntilIdle()
            repository.failSaves = false

            viewModel.onEvent(SettingsUiEvent.SelectModel("gpt-5.6-terra"))
            advanceUntilIdle()

            assertEquals(listOf(AppSettings(useDarkTheme = null, model = "gpt-5.6-terra")), repository.savedSettings)
        }

    @Test
    fun `two quick theme choices are saved in order and the last one wins`() =
        runTest(dispatcher.scheduler) {
            val repository = RecordingSettingsRepository(AppSettings())
            val viewModel = viewModelWith(repository)
            advanceUntilIdle()

            viewModel.onEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.DARK))
            viewModel.onEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.LIGHT))
            advanceUntilIdle()

            assertEquals(listOf(true, false), repository.savedSettings.map { it.useDarkTheme })
            assertEquals(ThemeMode.LIGHT, viewModel.state.value.themeMode)
        }

    @Test
    fun `nothing is written after the settings failed to load, so model and folder are never overwritten with defaults`() =
        runTest(dispatcher.scheduler) {
            val repository = RecordingSettingsRepository(AppSettings(model = "gpt-5.6-luna", searchFolderUri = "content://tree/1"))
            repository.failLoad = true
            val viewModel = viewModelWith(repository)
            advanceUntilIdle()

            viewModel.onEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.DARK))
            viewModel.onEvent(SettingsUiEvent.SetSystemColors(true))
            viewModel.onEvent(SettingsUiEvent.SelectModel("gpt-5.6-terra"))
            advanceUntilIdle()

            assertTrue(repository.savedSettings.isEmpty())
            assertTrue(viewModel.state.value.settingsLoadFailed)
            assertFalse(viewModel.state.value.canEditSettings)
        }

    @Test
    fun `nothing is written while the settings are still loading`() =
        runTest(dispatcher.scheduler) {
            val repository = RecordingSettingsRepository(AppSettings())
            val viewModel = viewModelWith(repository)
            assertFalse(viewModel.state.value.canEditSettings)

            viewModel.onEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.DARK))
            advanceUntilIdle()

            assertTrue(repository.savedSettings.isEmpty())
            assertTrue(viewModel.state.value.canEditSettings)
        }

    @Test
    fun `a cancelled save is not reported as a failure`() =
        runTest(dispatcher.scheduler) {
            val repository = RecordingSettingsRepository(AppSettings())
            val viewModel = viewModelWith(repository)
            advanceUntilIdle()
            repository.cancelSaves = true

            viewModel.onEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.DARK))
            advanceUntilIdle()

            assertFalse(viewModel.state.value.themeSaveFailed)
            assertFalse(viewModel.state.value.settingsUnavailable)
        }

    private fun viewModelWith(settingsRepository: SettingsRepository) =
        SettingsViewModel(
            authCoordinator = FakeAuthCoordinator(),
            settingsRepository = settingsRepository,
            modelCatalog = FakeModelCatalog(),
            conversationRepository = FakeConversationRepository(),
        )

    private class RecordingSettingsRepository(
        private var current: AppSettings,
    ) : SettingsRepository {
        val savedSettings = mutableListOf<AppSettings>()
        var failSaves = false
        var failLoad = false
        var cancelSaves = false

        override suspend fun load(): AppSettings {
            if (failLoad) throw java.io.IOException("cannot read settings")
            return current
        }

        override suspend fun save(settings: AppSettings) {
            if (cancelSaves) throw kotlinx.coroutines.CancellationException("save cancelled")
            if (failSaves) throw java.io.IOException("cannot write settings")
            savedSettings += settings
            current = settings
        }
    }

    private class FakeModelCatalog(
        private val result: ModelCatalogResult = ModelCatalogResult.Available(listOf("gpt-5.6-luna", "gpt-5.6-terra")),
    ) : ModelCatalog {
        override suspend fun load(): ModelCatalogResult = result
    }

    private class FakeAuthCoordinator(
        initial: AppAuthStatus = AppAuthStatus.SignedOut(),
    ) : AuthCoordinator {
        override val state = MutableStateFlow(initial)
        var signOutCalls = 0

        override suspend fun restore() = Unit

        override suspend fun signInWithBrowser() = Unit

        override suspend fun signInWithDeviceCode() = Unit

        override suspend fun signInWithApiKey(key: String) = Unit

        override suspend fun signOut() {
            signOutCalls += 1
        }

        override fun openDeviceVerificationPage() = Unit
    }

    private class FakeConversationRepository : ConversationRepository {
        val replacements = mutableListOf<List<HistoryItem>>()

        override suspend fun load(): List<HistoryItem> = emptyList()

        override suspend fun replace(items: List<HistoryItem>) {
            replacements += items
        }

        override fun observe(): Flow<List<HistoryItem>> = flowOf(emptyList())
    }
}
