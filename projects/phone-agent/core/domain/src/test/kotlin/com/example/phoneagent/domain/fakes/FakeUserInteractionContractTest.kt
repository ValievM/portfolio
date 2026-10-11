package com.example.phoneagent.domain.fakes

import com.example.phoneagent.domain.contracts.UserInteractionContract
import com.example.phoneagent.domain.interaction.UserInteraction

/** The shared fake obeys the same contract as the real bridge (see UserInteractionBridgeContractTest). */
class FakeUserInteractionContractTest : UserInteractionContract() {
    private val fake = FakeUserInteraction()

    override fun subject(): UserInteraction = fake

    override fun offer(approved: Boolean) {
        fake.scriptedApprovals += approved
    }

    override fun offerBatch(approved: Boolean) {
        fake.scriptedBatchApprovals += approved
    }
}
