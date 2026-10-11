package com.example.phoneagent.domain.agent

import com.example.phoneagent.domain.conversation.CallId
import com.example.phoneagent.domain.conversation.ContextWindowPolicy
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.conversation.ToolCall
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.model.ModelEvent
import com.example.phoneagent.domain.model.ModelGateway
import com.example.phoneagent.domain.model.ModelRequest
import com.example.phoneagent.domain.platform.IdGenerator
import com.example.phoneagent.domain.tools.Approval
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.ToolContent
import com.example.phoneagent.domain.tools.ToolOutcome
import com.example.phoneagent.domain.tools.ToolPipeline
import com.example.phoneagent.domain.tools.toOutputText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/**
 * Drives one user turn to completion: sends the model a request, executes any tool calls it asks
 * for (in order, per architecture/core.md — the model may request several in one response), feeds
 * their outputs back, and repeats until the model replies with no more calls, fails, or the step
 * limit is hit. A cold [Flow] — not tied to a ViewModel or Android; cancelling the collector
 * cancels the in-flight model call and tool execution with it (structured concurrency).
 */
public class AgentLoop(
    private val modelGateway: ModelGateway,
    private val toolPipeline: ToolPipeline,
    private val contextWindowPolicy: ContextWindowPolicy,
    private val idGenerator: IdGenerator,
    private val config: AgentConfig = AgentConfig(),
) {
    public fun run(
        history: List<HistoryItem>,
        input: String,
        onApprovalResolved: suspend (List<CallId>) -> Unit = {},
    ): Flow<AgentEvent> =
        flow {
            val newItems = mutableListOf<HistoryItem>(HistoryItem.UserMessage(idGenerator.newItemId(), input))
            var step = 0
            var toolCallsMade = 0
            while (true) {
                step++
                if (step > config.maxSteps) {
                    emit(AgentEvent.TurnFailed(AgentError.StepLimit, newItems.toList()))
                    return@flow
                }
                val turn = runModelTurn(history + newItems)
                if (turn.failure != null) {
                    emit(AgentEvent.TurnFailed(turn.failure, newItems.toList()))
                    return@flow
                }
                if (turn.text.isNotEmpty()) {
                    newItems += HistoryItem.AssistantMessage(idGenerator.newItemId(), turn.text)
                }
                if (turn.toolCalls.isEmpty()) {
                    emit(AgentEvent.TurnCompleted(newItems.toList()))
                    return@flow
                }
                if (toolCallsMade + turn.toolCalls.size > config.maxToolCallsPerTurn) {
                    // The step does not fit into the per-turn tool budget: reject it whole - no
                    // call in it runs, everything already done stays in history.
                    emit(AgentEvent.TurnFailed(AgentError.StepLimit, newItems.toList()))
                    return@flow
                }
                toolCallsMade += turn.toolCalls.size
                val sharedDecisions = confirmGroups(turn.toolCalls, onApprovalResolved)
                for (call in turn.toolCalls) {
                    newItems += HistoryItem.ToolCallItem(call.itemId, call.callId, call.name, call.arguments)
                    newItems += runToolCall(call, sharedDecisions[call.callId], onApprovalResolved)
                }
            }
        }

    private suspend fun FlowCollector<AgentEvent>.runModelTurn(history: List<HistoryItem>): ModelTurn {
        val text = StringBuilder()
        val toolCalls = mutableListOf<ToolCall>()
        var failure: AgentError? = null
        val request = ModelRequest(contextWindowPolicy.apply(history), toolPipeline.specs)
        modelGateway.stream(request).collect { event ->
            when (event) {
                is ModelEvent.TextDelta -> {
                    text.append(event.text)
                    emit(AgentEvent.TextDelta(event.text))
                }

                is ModelEvent.ToolCallRequested -> {
                    toolCalls += event.call
                }

                is ModelEvent.Failed -> {
                    failure = event.error
                }

                is ModelEvent.Completed, is ModelEvent.OpaqueItem -> {
                    Unit
                }
            }
        }
        return ModelTurn(text.toString(), toolCalls, failure)
    }

    /**
     * Asks one shared question per group of confirm-level calls (before anything runs), so the user
     * answers once for a batch instead of once per call. Returns the answer for each grouped call.
     */
    private suspend fun FlowCollector<AgentEvent>.confirmGroups(
        calls: List<ToolCall>,
        onApprovalResolved: suspend (List<CallId>) -> Unit,
    ): Map<CallId, Approval> {
        val decisions = mutableMapOf<CallId, Approval>()
        for (group in toolPipeline.batchGroups(calls)) {
            var approvalWasPresented = false
            val approved =
                toolPipeline.confirmBatch(group) { descriptions ->
                    group.zip(descriptions).forEach { (call, description) ->
                        emit(AgentEvent.ToolCallStarted(call, description ?: CallDescription(call.name, "")))
                    }
                    if (descriptions.all { it != null }) {
                        emit(AgentEvent.BatchApprovalRequested(group.map { it.callId }))
                        approvalWasPresented = true
                    }
                }
            val decision =
                when {
                    !approvalWasPresented -> Approval.NOT_PRESENTED
                    approved -> Approval.GRANTED
                    else -> Approval.DECLINED
                }
            if (approvalWasPresented) onApprovalResolved(group.map { it.callId })
            group.forEach { decisions[it.callId] = decision }
        }
        return decisions
    }

    /** What the card and the confirmation of [call] say; a tool that no longer exists is shown under its name. */
    public fun describe(call: ToolCall): CallDescription =
        toolPipeline.describe(call.name, call.arguments) ?: unknownToolDescription(call.name)

    /** Restores a tool's typed result from the unchanged output text stored in conversation history. */
    public fun restoreToolContent(
        name: String,
        outputText: String,
    ): ToolContent? = toolPipeline.restoreContent(name, outputText)

    /** [sharedDecision] is null when the call was not part of a shared question and is asked on its own. */
    private suspend fun FlowCollector<AgentEvent>.runToolCall(
        call: ToolCall,
        sharedDecision: Approval?,
        onApprovalResolved: suspend (List<CallId>) -> Unit,
    ): HistoryItem.ToolOutput {
        if (sharedDecision == null) {
            val description = describe(call)
            emit(AgentEvent.ToolCallStarted(call, description))
            toolPipeline.riskOf(call.name)?.takeIf { it != Risk.SAFE }?.let { risk ->
                emit(AgentEvent.ApprovalRequested(ApprovalRequest(description, risk, call.arguments)))
            }
        }
        val outcome =
            toolPipeline.run(call.name, call.arguments, sharedDecision ?: Approval.ASK) {
                onApprovalResolved(listOf(call.callId))
            }
        emit(AgentEvent.ToolCallFinished(call, outcome))
        return HistoryItem.ToolOutput(idGenerator.newItemId(), call.callId, outcome.toOutputText())
    }

    private fun unknownToolDescription(name: String): CallDescription = CallDescription(title = name, summary = "Unknown tool")

    private data class ModelTurn(
        val text: String,
        val toolCalls: List<ToolCall>,
        val failure: AgentError?,
    )
}
