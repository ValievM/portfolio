package com.example.phoneagent.domain.fakes

import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.contracts.ModelGatewayContract
import com.example.phoneagent.domain.conversation.ToolCall
import com.example.phoneagent.domain.model.ModelEvent
import com.example.phoneagent.domain.model.ModelGateway

/** The scripted fake obeys the same contract as the real gateway (see ResponsesGatewayContractTest). */
class FakeModelGatewayContractTest : ModelGatewayContract() {
    override fun replying(chunks: List<String>): ModelGateway =
        FakeModelGateway().apply { events = chunks.map(ModelEvent::TextDelta) + ModelEvent.Completed() }

    override fun callingTool(call: ToolCall): ModelGateway =
        FakeModelGateway().apply { events = listOf(ModelEvent.ToolCallRequested(call), ModelEvent.Completed()) }

    override fun failingWith(error: AgentError): ModelGateway = FakeModelGateway().apply { events = listOf(ModelEvent.Failed(error)) }
}
