package com.example.phoneagent.domain.contracts

import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.interaction.UserInteraction
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every [UserInteraction] must do, whoever implements it. The shared fake that other tests
 * use and the real bridge (app/interaction) both run these tests, so the fake cannot quietly
 * drift from the real thing. Answers are offered before the calls are made; an implementation
 * that holds its questions (the bridge) resolves the waiting one, an implementation that answers
 * immediately (the fake) applies the offered answer to the next call.
 */
public abstract class UserInteractionContract {
    protected abstract fun subject(): UserInteraction

    /** Offer the answer for the next single confirmation. */
    protected abstract fun offer(approved: Boolean)

    /** Offer the answer for the next batch confirmation. */
    protected abstract fun offerBatch(approved: Boolean)

    /** Runs inside each test's runTest scope; implementations that need background wiring use it. */
    protected open fun kotlinx.coroutines.test.TestScope.setUp(interaction: UserInteraction) {}

    @Test
    public fun `answers arrive at their own calls, one at a time, in order`() =
        runTest {
            offer(approved = true)
            offer(approved = false)
            val interaction = subject()
            setUp(interaction)

            val first = async { interaction.confirm(request("первый")) }
            advanceUntilIdle()
            val second = async { interaction.confirm(request("второй")) }
            advanceUntilIdle()

            assertTrue(first.await())
            assertFalse(second.await())
        }

    @Test
    public fun `cancelling a waiting caller does not disturb the others`() =
        runTest {
            offer(approved = true)
            val interaction = subject()
            setUp(interaction)

            val first = async { interaction.confirm(request("первый")) }
            advanceUntilIdle()
            val second = async { interaction.confirm(request("второй")) }
            advanceUntilIdle()
            second.cancel()
            advanceUntilIdle()

            assertTrue(first.await())

            // A caller submitted after the cancelled one still gets its own offered answer.
            offer(approved = false)
            val third = async { interaction.confirm(request("третий")) }
            advanceUntilIdle()
            assertFalse(third.await())
        }

    @Test
    public fun `confirmBatch returns one answer for the whole group`() =
        runTest {
            val interaction = subject()
            setUp(interaction)

            offerBatch(approved = true)
            val approved = async { interaction.confirmBatch(listOf(request("будильник"), request("уведомление"))) }
            advanceUntilIdle()
            assertTrue(approved.await())

            offerBatch(approved = false)
            val declined = async { interaction.confirmBatch(listOf(request("второй"), request("третий"))) }
            advanceUntilIdle()
            assertFalse(declined.await())
        }

    private fun request(title: String): ApprovalRequest = ApprovalRequest(CallDescription(title, title), Risk.CONFIRM, buildJsonObject {})
}
