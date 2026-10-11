package com.example.phoneagent.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class DomainContractsTest {
    @Test
    fun `stage one domain contracts are present`() {
        val requiredContracts =
            listOf(
                "com.example.phoneagent.domain.model.ModelGateway",
                "com.example.phoneagent.domain.model.ModelEvent",
                "com.example.phoneagent.domain.tools.Tool",
                "com.example.phoneagent.domain.tools.ToolSpec",
                "com.example.phoneagent.domain.tools.CallDescription",
                "com.example.phoneagent.domain.tools.Risk",
                "com.example.phoneagent.domain.interaction.UserInteraction",
                "com.example.phoneagent.domain.auth.CredentialsProvider",
                "com.example.phoneagent.domain.storage.ConversationRepository",
                "com.example.phoneagent.domain.storage.SettingsRepository",
                "com.example.phoneagent.domain.platform.Clock",
                "com.example.phoneagent.domain.platform.DispatcherProvider",
                "com.example.phoneagent.domain.platform.AppLogger",
                "com.example.phoneagent.domain.agent.AgentError",
                "com.example.phoneagent.domain.conversation.HistoryItem",
            )

        val missing = requiredContracts.filterNot(::classExists)

        assertTrue("Missing domain contracts: $missing", missing.isEmpty())
    }

    private fun classExists(name: String): Boolean = runCatching { Class.forName(name) }.isSuccess
}
