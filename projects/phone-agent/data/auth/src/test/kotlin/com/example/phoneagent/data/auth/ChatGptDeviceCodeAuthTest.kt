package com.example.phoneagent.data.auth

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatGptDeviceCodeAuthTest {
    @Test
    fun `shows code polls by server interval and exchanges authorization code`() =
        runTest {
            val deviceGateway = FakeDeviceCodeGateway(DeviceCodePollResult.Pending, authorized())
            val tokenGateway = FakeTokenGateway(AuthResult.Success(TOKENS))
            val auth = ChatGptDeviceCodeAuth(CONFIG, deviceGateway, tokenGateway)

            val result = async { auth.signIn() }
            runCurrent()

            assertEquals(DeviceCodePrompt("CODE-123", "https://auth.openai.com/device"), auth.prompt.value)
            assertEquals(1, deviceGateway.pollCount)
            advanceTimeBy(4_999L)
            assertEquals(1, deviceGateway.pollCount)
            advanceTimeBy(1L)
            advanceUntilIdle()

            assertEquals(AuthResult.Success(TOKENS.copy(method = AuthMethod.CHATGPT_DEVICE_CODE)), result.await())
            assertEquals(2, deviceGateway.pollCount)
            assertEquals("authorization-code", tokenGateway.exchange?.code)
            assertEquals("device-verifier", tokenGateway.exchange?.codeVerifier)
            assertEquals("https://auth.openai.com/deviceauth/callback", tokenGateway.exchange?.redirectUri)
            assertNull(auth.prompt.value)
        }

    @Test
    fun `pending device authorization times out after fifteen minutes with virtual time`() =
        runTest {
            val auth = ChatGptDeviceCodeAuth(CONFIG, FakeDeviceCodeGateway(DeviceCodePollResult.Pending), FakeTokenGateway())

            val result = async { auth.signIn() }
            advanceTimeBy(15 * 60 * 1_000L)
            advanceUntilIdle()

            assertEquals(AuthResult.Failure(AuthFailure.TimedOut), result.await())
        }

    @Test
    fun `device gateway network failure is returned as data`() =
        runTest {
            val auth =
                ChatGptDeviceCodeAuth(
                    CONFIG,
                    FakeDeviceCodeGateway(request = DeviceCodeRequestResult.Failure(AuthFailure.Network(null))),
                    FakeTokenGateway(),
                )

            assertEquals(AuthResult.Failure(AuthFailure.Network(null)), auth.signIn())
        }

    private fun authorized(): DeviceCodePollResult = DeviceCodePollResult.Authorized("authorization-code", "device-verifier")

    private class FakeDeviceCodeGateway(
        private vararg val polls: DeviceCodePollResult,
        private val request: DeviceCodeRequestResult =
            DeviceCodeRequestResult.Ready(
                deviceAuthId = "device-auth-id",
                userCode = "CODE-123",
                verificationUrl = "https://auth.openai.com/device",
                intervalSeconds = 5L,
            ),
    ) : DeviceCodeGateway {
        private val iterator = polls.iterator()
        var pollCount: Int = 0

        override suspend fun requestCode(): DeviceCodeRequestResult = request

        override suspend fun poll(
            deviceAuthId: String,
            userCode: String,
        ): DeviceCodePollResult {
            pollCount++
            return if (iterator.hasNext()) iterator.next() else polls.last()
        }
    }

    private class FakeTokenGateway(
        private val result: AuthResult = AuthResult.Failure(AuthFailure.Network(null)),
    ) : TokenRefreshGateway {
        var exchange: AuthorizationCodeExchange? = null

        override suspend fun exchangeAuthorizationCode(exchange: AuthorizationCodeExchange): AuthResult {
            this.exchange = exchange
            return result
        }

        override suspend fun refresh(refreshToken: String): RefreshResult = RefreshResult.Failure(AuthFailure.Network(null))
    }

    private companion object {
        private val CONFIG = AuthConfig.ChatGptSubscription

        // Method is deliberately wrong (not CHATGPT_DEVICE_CODE): the gateway is shared and
        // cannot know the real method, so the assertion above proves signIn() overrides it.
        private val TOKENS = AuthTokens("access", "id", "refresh", 100L, null, AuthMethod.CHATGPT_BROWSER)
    }
}
