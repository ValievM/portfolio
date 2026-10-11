package com.example.phoneagent.ui.login

import com.example.phoneagent.auth.AppAuthStatus
import com.example.phoneagent.auth.AuthCoordinator
import com.example.phoneagent.auth.AuthErrorReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loopback failure exposes device code fallback`() =
        runTest(dispatcher.scheduler) {
            val coordinator = FakeAuthCoordinator(AppAuthStatus.Failure(AuthErrorReason.LOOPBACK_PORT_UNAVAILABLE))
            val viewModel = LoginViewModel(coordinator)

            viewModel.state.collectInBackground(backgroundScope)
            advanceUntilIdle()

            assertEquals(LoginError.LOOPBACK_PORT_UNAVAILABLE, viewModel.state.value.error)
            assertTrue(viewModel.state.value.showDeviceCodeFallback)
        }

    @Test
    fun `device prompt is translated without business logic`() =
        runTest(dispatcher.scheduler) {
            val coordinator = FakeAuthCoordinator(AppAuthStatus.DeviceCode("CODE-123", "https://example.test/device"))
            val viewModel = LoginViewModel(coordinator)

            viewModel.state.collectInBackground(backgroundScope)
            advanceUntilIdle()

            assertEquals("CODE-123", viewModel.state.value.deviceCode)
            assertTrue(viewModel.state.value.isLoading)
        }

    private fun kotlinx.coroutines.flow.StateFlow<LoginUiState>.collectInBackground(scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch { collect {} }
    }

    private class FakeAuthCoordinator(
        initial: AppAuthStatus,
    ) : AuthCoordinator {
        override val state = MutableStateFlow(initial)

        override suspend fun restore() = Unit

        override suspend fun signInWithBrowser() = Unit

        override suspend fun signInWithDeviceCode() = Unit

        override suspend fun signInWithApiKey(key: String) = Unit

        override suspend fun signOut() = Unit

        override fun openDeviceVerificationPage() = Unit
    }
}
