package com.example.phoneagent.interaction

import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserInteractionBridgeTest {
    private val bridge = UserInteractionBridge()

    @Test
    fun `a batch question stays pending until answered and returns the answer`() =
        runTest {
            val requests = listOf(request("06:00"), request("06:15"))

            val answer = async { bridge.confirmBatch(requests) }
            advanceUntilIdle()

            val pending = bridge.pending.value as PendingInteraction.BatchApproval
            assertEquals(requests, pending.requests)
            pending.deferred.complete(true)
            assertTrue(answer.await())
            assertNull(bridge.pending.value)
        }

    @Test
    fun `leaving the screen declines a pending batch`() =
        runTest {
            val answer = async { bridge.confirmBatch(listOf(request("06:00"), request("06:15"))) }
            advanceUntilIdle()

            bridge.resolvePendingAsDeclined()

            assertFalse(answer.await())
            assertNull(bridge.pending.value)
        }

    @Test
    fun `cancelling the caller clears a pending batch`() =
        runTest {
            val job = launch { bridge.confirmBatch(listOf(request("06:00"), request("06:15"))) }
            advanceUntilIdle()
            assertTrue(bridge.pending.value is PendingInteraction.BatchApproval)

            job.cancel()
            advanceUntilIdle()

            assertNull(bridge.pending.value)
        }

    private fun request(time: String) =
        ApprovalRequest(CallDescription("Поставить будильник", "На $time"), Risk.CONFIRM, buildJsonObject {})
}
