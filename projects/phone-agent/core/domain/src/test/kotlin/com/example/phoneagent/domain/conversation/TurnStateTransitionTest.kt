package com.example.phoneagent.domain.conversation

import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.conversation.PendingApproval.Single
import com.example.phoneagent.domain.conversation.TurnEvent.ApprovalRequired
import com.example.phoneagent.domain.conversation.TurnEvent.ApprovalResolved
import com.example.phoneagent.domain.conversation.TurnEvent.Completed
import com.example.phoneagent.domain.conversation.TurnEvent.DismissFailure
import com.example.phoneagent.domain.conversation.TurnEvent.ExternalHistoryChanged
import com.example.phoneagent.domain.conversation.TurnEvent.Failed
import com.example.phoneagent.domain.conversation.TurnEvent.RestoredUnanswered
import com.example.phoneagent.domain.conversation.TurnEvent.Retry
import com.example.phoneagent.domain.conversation.TurnEvent.Stop
import com.example.phoneagent.domain.conversation.TurnEvent.Submitted
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class TurnStateTransitionTest {
    @Test
    fun `turn transitions follow the state event decision table`() {
        val cases =
            listOf(
                startAndApprovalCases(),
                completionAndStopCases(),
                failureAndRetryCases(),
                dismissalAndHistoryCases(),
            ).flatten()

        cases.forEach { case ->
            assertEquals(case.name, case.expected, transitionTurn(case.state, case.event))
        }
    }

    private fun startAndApprovalCases(): List<TransitionCase> {
        val questionId = ItemId("question-1")
        val nextQuestionId = ItemId("question-2")
        val callId = CallId("call-1")
        val singlePending = PendingApproval.Single(callId, approvalRequest("Alarm"))
        val singleAwaiting = TurnState.AwaitingApproval(questionId, singlePending)
        val batchCallIds = listOf(CallId("call-1"), CallId("call-2"))
        val batchPending = PendingApproval.Batch(batchCallIds.map { ApprovedCall(it, approvalRequest(it.value)) })
        val batchAwaiting = TurnState.AwaitingApproval(questionId, batchPending)
        val running = TurnState.Running(questionId)
        return listOf(
            TransitionCase("idle accepts submit", TurnState.Idle, Submitted(questionId), running),
            TransitionCase("active ignores submit", running, Submitted(nextQuestionId), running),
            TransitionCase("idle ignores approval", TurnState.Idle, ApprovalRequired(singlePending), TurnState.Idle),
            TransitionCase("running stores one exact approval", running, ApprovalRequired(singlePending), singleAwaiting),
            TransitionCase("running stores an ordered approval batch", running, ApprovalRequired(batchPending), batchAwaiting),
            TransitionCase("matching single answer resumes", singleAwaiting, ApprovalResolved(listOf(callId)), running),
            TransitionCase("wrong single answer is ignored", singleAwaiting, ApprovalResolved(listOf(CallId("other"))), singleAwaiting),
            TransitionCase("matching batch answer resumes", batchAwaiting, ApprovalResolved(batchCallIds), running),
            TransitionCase(
                "reordered batch answer is ignored",
                batchAwaiting,
                ApprovalResolved(batchCallIds.reversed()),
                batchAwaiting,
            ),
        )
    }

    private fun completionAndStopCases(): List<TransitionCase> {
        val questionId = ItemId("question-1")
        val nextQuestionId = ItemId("question-2")
        val running = TurnState.Running(questionId)
        val awaiting = TurnState.AwaitingApproval(questionId, PendingApproval.Single(CallId("call-1"), approvalRequest("Alarm")))
        val stopped = TurnState.Stopped(questionId)
        val interrupted = TurnState.Interrupted(questionId)
        return listOf(
            TransitionCase("completed turn becomes idle", running, Completed, TurnState.Idle),
            TransitionCase("awaiting approval cannot complete", awaiting, Completed, awaiting),
            TransitionCase("stop preserves active question", running, Stop, stopped),
            TransitionCase("stop preserves question while approval is open", awaiting, Stop, stopped),
            TransitionCase("idle ignores stop", TurnState.Idle, Stop, TurnState.Idle),
            TransitionCase("startup question becomes interrupted", TurnState.Idle, RestoredUnanswered(questionId), interrupted),
            TransitionCase("running ignores startup restore", running, RestoredUnanswered(nextQuestionId), running),
        )
    }

    private fun failureAndRetryCases(): List<TransitionCase> {
        val questionId = ItemId("question-1")
        val nextQuestionId = ItemId("question-2")
        val running = TurnState.Running(questionId)
        val interrupted = TurnState.Interrupted(questionId)
        val retryableFailure = TurnState.FailedRetryable(questionId, AgentError.Timeout)
        val terminalFailure = TurnState.FailedTerminal(AgentError.Forbidden)
        val awaiting = TurnState.AwaitingApproval(questionId, PendingApproval.Single(CallId("call-1"), approvalRequest("Alarm")))
        val stopped = TurnState.Stopped(ItemId("stopped-question"))
        return listOf(
            TransitionCase("retryable error keeps question", running, Failed(AgentError.Timeout, true), retryableFailure),
            TransitionCase("terminal error is distinct", running, Failed(AgentError.Forbidden, false), terminalFailure),
            TransitionCase(
                "approval failure keeps retryable error",
                awaiting,
                Failed(AgentError.Offline, true),
                TurnState.FailedRetryable(questionId, AgentError.Offline),
            ),
            TransitionCase("retry resumes interrupted question", interrupted, Retry(nextQuestionId), TurnState.Running(nextQuestionId)),
            TransitionCase(
                "retry resumes retryable failure",
                retryableFailure,
                Retry(nextQuestionId),
                TurnState.Running(nextQuestionId),
            ),
            TransitionCase("retry is forbidden for terminal failure", terminalFailure, Retry(nextQuestionId), terminalFailure),
            TransitionCase("new submit after stop continues", stopped, Submitted(nextQuestionId), TurnState.Running(nextQuestionId)),
            TransitionCase(
                "new submit after terminal failure starts",
                terminalFailure,
                Submitted(nextQuestionId),
                TurnState.Running(nextQuestionId),
            ),
        )
    }

    private fun dismissalAndHistoryCases(): List<TransitionCase> {
        val questionId = ItemId("question-1")
        val nextQuestionId = ItemId("question-2")
        val running = TurnState.Running(questionId)
        val interrupted = TurnState.Interrupted(questionId)
        val retryableFailure = TurnState.FailedRetryable(questionId, AgentError.Timeout)
        val terminalFailure = TurnState.FailedTerminal(AgentError.Forbidden)
        return listOf(
            TransitionCase("dismiss clears interrupted notice", interrupted, DismissFailure, TurnState.Idle),
            TransitionCase("dismiss clears retryable failure", retryableFailure, DismissFailure, TurnState.Idle),
            TransitionCase("dismiss clears terminal failure", terminalFailure, DismissFailure, TurnState.Idle),
            TransitionCase("external clear cancels active turn", running, ExternalHistoryChanged(null), TurnState.Idle),
            TransitionCase(
                "external unanswered history becomes interrupted",
                running,
                ExternalHistoryChanged(nextQuestionId),
                TurnState.Interrupted(nextQuestionId),
            ),
        )
    }

    @Test
    fun `submitting from idle starts the requested turn`() {
        val questionId = ItemId("question-1")

        assertEquals(TurnState.Running(questionId), transitionTurn(TurnState.Idle, Submitted(questionId)))
    }

    @Test
    fun `submitting while a turn runs leaves its state unchanged`() {
        val running = TurnState.Running(ItemId("question-1"))

        assertEquals(running, transitionTurn(running, Submitted(ItemId("question-2"))))
    }

    @Test
    fun `approval request stores the exact call and user-facing details`() {
        val questionId = ItemId("question-1")
        val callId = CallId("call-1")
        val request = ApprovalRequest(CallDescription("Set alarm", "At 20:00"), Risk.CONFIRM, buildJsonObject {})

        assertEquals(
            TurnState.AwaitingApproval(questionId, Single(callId, request)),
            transitionTurn(TurnState.Running(questionId), ApprovalRequired(Single(callId, request))),
        )
    }

    @Test
    fun `batch approval preserves the exact call order and descriptions`() {
        val questionId = ItemId("question-1")
        val first = ApprovedCall(CallId("call-1"), approvalRequest("First"))
        val second = ApprovedCall(CallId("call-2"), approvalRequest("Second"))
        val pending = PendingApproval.Batch(listOf(first, second))

        assertEquals(
            TurnState.AwaitingApproval(questionId, pending),
            transitionTurn(TurnState.Running(questionId), ApprovalRequired(pending)),
        )
    }

    @Test
    fun `resolving approval resumes the same turn`() {
        val questionId = ItemId("question-1")
        val callId = CallId("call-1")
        val awaiting = TurnState.AwaitingApproval(questionId, PendingApproval.Single(callId, approvalRequest("Alarm")))

        assertEquals(TurnState.Running(questionId), transitionTurn(awaiting, ApprovalResolved(listOf(callId))))
        assertEquals(awaiting, transitionTurn(awaiting, ApprovalResolved(listOf(CallId("unexpected")))))
    }

    @Test
    fun `batch resolution must match the exact ordered call set`() {
        val questionId = ItemId("question-1")
        val callIds = listOf(CallId("call-1"), CallId("call-2"))
        val pending = PendingApproval.Batch(callIds.map { ApprovedCall(it, approvalRequest(it.value)) })
        val awaiting = TurnState.AwaitingApproval(questionId, pending)

        assertEquals(TurnState.Running(questionId), transitionTurn(awaiting, ApprovalResolved(callIds)))
        assertEquals(awaiting, transitionTurn(awaiting, ApprovalResolved(callIds.reversed())))
    }

    @Test
    fun `completion returns a running turn to idle`() {
        assertEquals(TurnState.Idle, transitionTurn(TurnState.Running(ItemId("question-1")), Completed))
    }

    @Test
    fun `stop keeps the question and marks the turn stopped`() {
        val questionId = ItemId("question-1")

        assertEquals(TurnState.Stopped(questionId), transitionTurn(TurnState.Running(questionId), Stop))
    }

    @Test
    fun `stopping approval leaves the question and accepts a later submit`() {
        val firstQuestion = ItemId("question-1")
        val secondQuestion = ItemId("question-2")
        val awaiting = TurnState.AwaitingApproval(firstQuestion, PendingApproval.Single(CallId("call-1"), approvalRequest("Alarm")))
        val stopped = transitionTurn(awaiting, Stop)

        assertEquals(TurnState.Stopped(firstQuestion), stopped)
        assertEquals(TurnState.Running(secondQuestion), transitionTurn(stopped, Submitted(secondQuestion)))
    }

    @Test
    fun `restored unanswered question is interrupted`() {
        val questionId = ItemId("question-1")

        assertEquals(TurnState.Interrupted(questionId), transitionTurn(TurnState.Idle, RestoredUnanswered(questionId)))
    }

    @Test
    fun `retry is accepted only from interrupted or retryable failure`() {
        val retryId = ItemId("retry-question")
        val interrupted = TurnState.Interrupted(ItemId("old-question"))
        val retryable = TurnState.FailedRetryable(ItemId("old-question"), AgentError.Offline)
        val terminal = TurnState.FailedTerminal(AgentError.Forbidden)

        assertEquals(TurnState.Running(retryId), transitionTurn(interrupted, Retry(retryId)))
        assertEquals(TurnState.Running(retryId), transitionTurn(retryable, Retry(retryId)))
        assertEquals(terminal, transitionTurn(terminal, Retry(retryId)))
    }

    @Test
    fun `retry does nothing from idle, a running or approving turn, a stopped turn or a terminal failure`() {
        val retryId = ItemId("retry-question")
        val request = approvalRequest("Set alarm")
        val states =
            listOf(
                TurnState.Idle,
                TurnState.Running(ItemId("q")),
                TurnState.AwaitingApproval(ItemId("q"), PendingApproval.Single(CallId("call-1"), request)),
                TurnState.Stopped(ItemId("q")),
                TurnState.FailedTerminal(AgentError.Forbidden),
            )

        states.forEach { state -> assertEquals("retry must not change $state", state, transitionTurn(state, TurnEvent.Retry(retryId))) }
    }

    @Test
    fun `failure event selects retryable or terminal state`() {
        val questionId = ItemId("question-1")
        val running = TurnState.Running(questionId)

        assertEquals(
            TurnState.FailedRetryable(questionId, AgentError.Timeout),
            transitionTurn(running, Failed(AgentError.Timeout, retryable = true)),
        )
        assertEquals(
            TurnState.FailedTerminal(AgentError.Forbidden),
            transitionTurn(running, Failed(AgentError.Forbidden, retryable = false)),
        )
    }

    @Test
    fun `new submit starts from every non-running turn state`() {
        val questionId = ItemId("new-question")
        val beforeStates =
            listOf(
                TurnState.Idle,
                TurnState.Stopped(ItemId("stopped-question")),
                TurnState.Interrupted(ItemId("interrupted-question")),
                TurnState.FailedRetryable(ItemId("failed-question"), AgentError.Offline),
                TurnState.FailedTerminal(AgentError.Forbidden),
            )

        beforeStates.forEach { before ->
            assertEquals(before.toString(), TurnState.Running(questionId), transitionTurn(before, Submitted(questionId)))
        }
    }

    @Test
    fun `dismissing a failure clears its error and retry affordance`() {
        assertEquals(
            TurnState.Idle,
            transitionTurn(TurnState.FailedRetryable(ItemId("question-1"), AgentError.Timeout), DismissFailure),
        )
        assertEquals(TurnState.Idle, transitionTurn(TurnState.FailedTerminal(AgentError.Forbidden), DismissFailure))
        assertEquals(TurnState.Idle, transitionTurn(TurnState.Interrupted(ItemId("question-1")), DismissFailure))
    }

    @Test
    fun `external history replaces active state with idle or interrupted`() {
        val running = TurnState.Running(ItemId("old-question"))
        val unansweredQuestion = ItemId("restored-question")

        assertEquals(TurnState.Idle, transitionTurn(running, ExternalHistoryChanged(null)))
        assertEquals(
            TurnState.Interrupted(unansweredQuestion),
            transitionTurn(running, ExternalHistoryChanged(unansweredQuestion)),
        )
    }

    private fun approvalRequest(title: String) = ApprovalRequest(CallDescription(title, "summary"), Risk.CONFIRM, buildJsonObject {})

    private data class TransitionCase(
        val name: String,
        val state: TurnState,
        val event: TurnEvent,
        val expected: TurnState,
    )
}
