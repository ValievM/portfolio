package com.example.phoneagent.data.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.phoneagent.domain.fakes.FakeAppLogger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedTokenStoreTest {
    @Test
    fun writesEncryptedTokensReadsAndClears() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val store = EncryptedTokenStore(context, FakeAppLogger())
            val tokens =
                AuthTokens(
                    accessToken = "instrumented-access-token",
                    idToken = "instrumented-id-token",
                    refreshToken = "instrumented-refresh-token",
                    accessTokenExpiresAtEpochSeconds = 200L,
                    earliestRefreshAtEpochSeconds = 100L,
                    method = AuthMethod.CHATGPT_DEVICE_CODE,
                )

            store.clear()
            try {
                store.write(tokens)

                assertEquals(tokens, store.read())
                val bytes = context.filesDir.resolve("datastore/auth_tokens.preferences_pb").readBytes()
                assertFalse(bytes.decodeToString().contains("instrumented-access-token"))
                assertFalse(bytes.decodeToString().contains("instrumented-id-token"))
                assertFalse(bytes.decodeToString().contains("instrumented-refresh-token"))
            } finally {
                store.clear()
            }
            assertNull(store.read())
        }
}
