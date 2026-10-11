package com.example.phoneagent.domain.fakes

import com.example.phoneagent.domain.auth.Credentials
import com.example.phoneagent.domain.auth.CredentialsProvider
import com.example.phoneagent.domain.auth.CredentialsResult
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.conversation.ItemId
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.interaction.Capability
import com.example.phoneagent.domain.interaction.CapabilityResult
import com.example.phoneagent.domain.interaction.UserInteraction
import com.example.phoneagent.domain.model.ModelEvent
import com.example.phoneagent.domain.model.ModelGateway
import com.example.phoneagent.domain.model.ModelRequest
import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.Clock
import com.example.phoneagent.domain.platform.DispatcherProvider
import com.example.phoneagent.domain.platform.IdGenerator
import com.example.phoneagent.domain.platform.LogLevel
import com.example.phoneagent.domain.storage.AppSettings
import com.example.phoneagent.domain.storage.ConversationRepository
import com.example.phoneagent.domain.storage.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

public class FakeModelGateway : ModelGateway {
    public val requests: MutableList<ModelRequest> = mutableListOf()

    /** Replayed on every call once [responseQueue] is empty; the simple single-response case. */
    public var events: List<ModelEvent> = emptyList()

    /** One entry per call, consumed in order; lets a test script a multi-step conversation. */
    public val responseQueue: ArrayDeque<List<ModelEvent>> = ArrayDeque()

    override fun stream(request: ModelRequest): Flow<ModelEvent> =
        flow {
            requests += request
            val toEmit = if (responseQueue.isNotEmpty()) responseQueue.removeFirst() else events
            toEmit.forEach { emit(it) }
        }
}

public class FakeConversationRepository : ConversationRepository {
    private val mutableItems = MutableStateFlow<List<HistoryItem>>(emptyList())

    public var items: List<HistoryItem>
        get() = mutableItems.value
        set(value) {
            mutableItems.value = value
        }

    override suspend fun load(): List<HistoryItem> = items

    override fun observe(): Flow<List<HistoryItem>> = mutableItems

    override suspend fun replace(items: List<HistoryItem>) {
        this.items = items
    }
}

public class FakeSettingsRepository : SettingsRepository {
    public var settings: AppSettings = AppSettings()

    override suspend fun load(): AppSettings = settings

    override suspend fun save(settings: AppSettings) {
        this.settings = settings
    }
}

/**
 * Behaves like the real session manager (see CredentialsProviderContract): a forced refresh of a session that is
 * temporarily unavailable stays unavailable, and a refresh that succeeds (or ends the session) is what
 * [current] returns afterwards.
 */
public class FakeCredentialsProvider(
    public var currentResult: CredentialsResult,
) : CredentialsProvider {
    public var refreshedCredentials: Credentials? = null

    /** Overrides [refreshedCredentials] when a test needs the refresh itself to have a specific outcome. */
    public var refreshResult: CredentialsResult? = null

    override suspend fun current(): CredentialsResult = currentResult

    override suspend fun refreshAfterUnauthorized(): CredentialsResult {
        val result =
            refreshResult
                ?: refreshedCredentials?.let(CredentialsResult::Ready)
                ?: if (currentResult == CredentialsResult.Unavailable) CredentialsResult.Unavailable else CredentialsResult.SignedOut
        if (result != CredentialsResult.Unavailable) currentResult = result
        return result
    }
}

public class FakeClock(
    public var epochMillis: Long = 0L,
) : Clock {
    override fun nowEpochMillis(): Long = epochMillis
}

public class FakeIdGenerator : IdGenerator {
    private var counter = 0

    override fun newItemId(): ItemId = ItemId("id-${++counter}")
}

public class FakeDispatcherProvider(
    override val main: CoroutineDispatcher,
    override val io: CoroutineDispatcher = main,
    override val default: CoroutineDispatcher = main,
) : DispatcherProvider

public class FakeUserInteraction : UserInteraction {
    public val approvalRequests: MutableList<ApprovalRequest> = mutableListOf()
    public val capabilityRequests: MutableList<Capability> = mutableListOf()
    public val batchRequests: MutableList<List<ApprovalRequest>> = mutableListOf()
    public var approvalResult: Boolean = true
    public var batchApprovalResult: Boolean = true
    public var capabilityResult: CapabilityResult = CapabilityResult.Available

    /**
     * Per-call answers handed out one at a time, in call order (the UserInteractionContract way to
     * say "answers arrive at their own calls"). When a queue is empty, the call falls back to the
     * single [approvalResult]/[batchApprovalResult], which is what every existing test relies on.
     */
    public val scriptedApprovals: ArrayDeque<Boolean> = ArrayDeque()
    public val scriptedBatchApprovals: ArrayDeque<Boolean> = ArrayDeque()

    override suspend fun confirm(request: ApprovalRequest): Boolean {
        approvalRequests += request
        return scriptedApprovals.removeFirstOrNull() ?: approvalResult
    }

    override suspend fun confirmBatch(requests: List<ApprovalRequest>): Boolean {
        batchRequests += requests
        return scriptedBatchApprovals.removeFirstOrNull() ?: batchApprovalResult
    }

    override suspend fun ensure(capability: Capability): CapabilityResult {
        capabilityRequests += capability
        return capabilityResult
    }
}

public data class LoggedEvent(
    public val level: LogLevel,
    public val tag: String,
    public val message: String,
    public val fields: Map<String, String>,
    public val throwable: Throwable?,
)

public class FakeAppLogger : AppLogger {
    public val events: MutableList<LoggedEvent> = mutableListOf()

    override fun log(
        level: LogLevel,
        tag: String,
        message: String,
        fields: Map<String, String>,
        throwable: Throwable?,
    ) {
        events += LoggedEvent(level, tag, message, fields, throwable)
    }
}
