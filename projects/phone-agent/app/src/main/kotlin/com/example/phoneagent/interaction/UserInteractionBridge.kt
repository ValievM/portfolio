package com.example.phoneagent.interaction

import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.interaction.Capability
import com.example.phoneagent.domain.interaction.CapabilityResult
import com.example.phoneagent.domain.interaction.UserInteraction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** One question the core is waiting on; the composable that resolves it drives [deferred]. */
public sealed interface PendingInteraction {
    public val deferredAny: CompletableDeferred<*>

    public data class Approval(
        public val request: ApprovalRequest,
        public val deferred: CompletableDeferred<Boolean>,
    ) : PendingInteraction {
        override val deferredAny: CompletableDeferred<*> get() = deferred
    }

    public data class BatchApproval(
        public val requests: List<ApprovalRequest>,
        public val deferred: CompletableDeferred<Boolean>,
    ) : PendingInteraction {
        override val deferredAny: CompletableDeferred<*> get() = deferred
    }

    public data class Permission(
        public val capability: Capability,
        public val deferred: CompletableDeferred<CapabilityResult>,
    ) : PendingInteraction {
        override val deferredAny: CompletableDeferred<*> get() = deferred
    }
}

/**
 * The bridge between the (Android-free) core asking a question via [UserInteraction] and whatever
 * is on screen answering it — see architecture/adapters.md "UserInteractionBridge — самое хитрое
 * место". The core suspends on the entry's deferred; a root composable (`InteractionHost`)
 * observes [pending], shows the appropriate UI, and completes it.
 *
 * Questions are served strictly one at a time in arrival order: [pending] is the head of the
 * queue, and the next question is published as soon as the current one is answered or cancelled.
 * Cancelling an awaiting caller removes only that caller's own entry. If the collector of
 * [pending] goes away before resolving (screen destroyed, host disposed), every question —
 * current and waiting — is declined via [resolvePendingAsDeclined] rather than left hanging.
 */
@Singleton
public class UserInteractionBridge
    @Inject
    constructor() : UserInteraction {
        private val lock = Any()
        private val queue = ArrayDeque<PendingInteraction>()
        private val mutablePending = MutableStateFlow<PendingInteraction?>(null)
        public val pending: StateFlow<PendingInteraction?> = mutablePending.asStateFlow()

        override suspend fun confirm(request: ApprovalRequest): Boolean {
            val entry = PendingInteraction.Approval(request, CompletableDeferred())
            return awaitEntry(entry) { entry.deferred.await() }
        }

        override suspend fun confirmBatch(requests: List<ApprovalRequest>): Boolean {
            val entry = PendingInteraction.BatchApproval(requests, CompletableDeferred())
            return awaitEntry(entry) { entry.deferred.await() }
        }

        override suspend fun ensure(capability: Capability): CapabilityResult {
            val entry = PendingInteraction.Permission(capability, CompletableDeferred())
            return awaitEntry(entry) { entry.deferred.await() }
        }

        /** Called by the host when it leaves composition with question(s) unanswered. */
        public fun resolvePendingAsDeclined() {
            val snapshot =
                synchronized(lock) {
                    queue.toList()
                }
            for (entry in snapshot) {
                when (entry) {
                    is PendingInteraction.Approval -> entry.deferred.complete(false)
                    is PendingInteraction.BatchApproval -> entry.deferred.complete(false)
                    is PendingInteraction.Permission -> entry.deferred.complete(CapabilityResult.Denied)
                }
            }
        }

        private suspend fun <T> awaitEntry(
            entry: PendingInteraction,
            await: suspend () -> T,
        ): T {
            enqueue(entry)
            return try {
                await()
            } finally {
                settle(entry)
            }
        }

        private fun enqueue(entry: PendingInteraction) {
            synchronized(lock) {
                queue.addLast(entry)
                if (mutablePending.value == null) mutablePending.value = entry
            }
            // Fires as soon as the host completes the deferred; the caller's finally is the
            // second, idempotent settle path for cancellation, where nobody completes it.
            entry.deferredAny.invokeOnCompletion { settle(entry) }
        }

        private fun settle(entry: PendingInteraction) {
            synchronized(lock) {
                queue.remove(entry)
                if (mutablePending.value === entry) {
                    mutablePending.value = queue.firstOrNull()
                }
            }
        }
    }
