package com.example.phoneagent.domain.conversation

import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.interaction.ApprovalRequest

public sealed interface TurnState {
    public data object Idle : TurnState

    public data class Running(
        public val questionId: ItemId,
    ) : TurnState

    public data class AwaitingApproval(
        public val questionId: ItemId,
        public val pending: PendingApproval,
    ) : TurnState

    public data class Stopped(
        public val questionId: ItemId,
    ) : TurnState

    public data class Interrupted(
        public val questionId: ItemId,
    ) : TurnState

    public data class FailedRetryable(
        public val questionId: ItemId,
        public val error: AgentError,
    ) : TurnState

    public data class FailedTerminal(
        public val error: AgentError,
    ) : TurnState
}

public sealed interface PendingApproval {
    public data class Single(
        public val callId: CallId,
        public val request: ApprovalRequest,
    ) : PendingApproval

    public data class Batch(
        public val calls: List<ApprovedCall>,
    ) : PendingApproval
}

public data class ApprovedCall(
    public val callId: CallId,
    public val request: ApprovalRequest,
)

public sealed interface TurnEvent {
    public data class Submitted(
        public val questionId: ItemId,
    ) : TurnEvent

    public data class ApprovalRequired(
        public val pending: PendingApproval,
    ) : TurnEvent

    public data class ApprovalResolved(
        public val callIds: List<CallId>,
    ) : TurnEvent

    public data object Completed : TurnEvent

    public data object Stop : TurnEvent

    public data class RestoredUnanswered(
        public val questionId: ItemId,
    ) : TurnEvent

    public data class Failed(
        public val error: AgentError,
        public val retryable: Boolean,
    ) : TurnEvent

    public data class Retry(
        public val questionId: ItemId,
    ) : TurnEvent

    public data object DismissFailure : TurnEvent

    public data class ExternalHistoryChanged(
        public val unansweredQuestionId: ItemId?,
    ) : TurnEvent
}

/** Applies one turn event without side effects; unsupported events leave the state unchanged. */
public fun transitionTurn(
    state: TurnState,
    event: TurnEvent,
): TurnState =
    when (event) {
        is TurnEvent.Submitted -> submitted(state, event.questionId)
        is TurnEvent.ApprovalRequired -> approvalRequired(state, event.pending)
        is TurnEvent.ApprovalResolved -> approvalResolved(state, event.callIds)
        TurnEvent.Completed -> if (state is TurnState.Running) TurnState.Idle else state
        TurnEvent.Stop -> stopped(state)
        is TurnEvent.RestoredUnanswered -> restoredUnanswered(state, event.questionId)
        is TurnEvent.Failed -> failed(state, event)
        is TurnEvent.Retry -> retry(state, event.questionId)
        TurnEvent.DismissFailure -> dismissFailure(state)
        is TurnEvent.ExternalHistoryChanged -> event.unansweredQuestionId?.let(TurnState::Interrupted) ?: TurnState.Idle
    }

private fun submitted(
    state: TurnState,
    questionId: ItemId,
): TurnState =
    when (state) {
        TurnState.Idle,
        is TurnState.Stopped,
        is TurnState.Interrupted,
        is TurnState.FailedRetryable,
        is TurnState.FailedTerminal,
        -> TurnState.Running(questionId)

        is TurnState.Running, is TurnState.AwaitingApproval -> state
    }

private fun approvalRequired(
    state: TurnState,
    pending: PendingApproval,
): TurnState =
    when (state) {
        is TurnState.Running -> TurnState.AwaitingApproval(state.questionId, pending)

        TurnState.Idle, is TurnState.AwaitingApproval, is TurnState.Stopped, is TurnState.Interrupted,
        is TurnState.FailedRetryable, is TurnState.FailedTerminal,
        -> state
    }

private fun approvalResolved(
    state: TurnState,
    callIds: List<CallId>,
): TurnState {
    if (state !is TurnState.AwaitingApproval) return state
    val expectedCallIds =
        when (val pending = state.pending) {
            is PendingApproval.Single -> listOf(pending.callId)
            is PendingApproval.Batch -> pending.calls.map(ApprovedCall::callId)
        }
    return if (callIds == expectedCallIds) TurnState.Running(state.questionId) else state
}

private fun stopped(state: TurnState): TurnState =
    when (state) {
        is TurnState.Running -> TurnState.Stopped(state.questionId)

        is TurnState.AwaitingApproval -> TurnState.Stopped(state.questionId)

        TurnState.Idle,
        is TurnState.Stopped,
        is TurnState.Interrupted,
        is TurnState.FailedRetryable,
        is TurnState.FailedTerminal,
        -> state
    }

private fun restoredUnanswered(
    state: TurnState,
    questionId: ItemId,
): TurnState = if (state == TurnState.Idle) TurnState.Interrupted(questionId) else state

private fun failed(
    state: TurnState,
    event: TurnEvent.Failed,
): TurnState {
    val questionId =
        when (state) {
            is TurnState.Running -> state.questionId

            is TurnState.AwaitingApproval -> state.questionId

            TurnState.Idle,
            is TurnState.Stopped,
            is TurnState.Interrupted,
            is TurnState.FailedRetryable,
            is TurnState.FailedTerminal,
            -> return state
        }
    return if (event.retryable) TurnState.FailedRetryable(questionId, event.error) else TurnState.FailedTerminal(event.error)
}

private fun retry(
    state: TurnState,
    questionId: ItemId,
): TurnState =
    when (state) {
        is TurnState.Interrupted, is TurnState.FailedRetryable -> TurnState.Running(questionId)
        TurnState.Idle, is TurnState.Running, is TurnState.AwaitingApproval, is TurnState.Stopped, is TurnState.FailedTerminal -> state
    }

private fun dismissFailure(state: TurnState): TurnState =
    when (state) {
        is TurnState.FailedRetryable, is TurnState.FailedTerminal, is TurnState.Interrupted -> TurnState.Idle
        TurnState.Idle, is TurnState.Running, is TurnState.AwaitingApproval, is TurnState.Stopped -> state
    }
