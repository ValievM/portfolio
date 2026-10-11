package com.example.phoneagent.data.llm

import com.example.phoneagent.data.llm.request.buildResponsesRequestBody
import com.example.phoneagent.data.llm.sse.SseParser
import com.example.phoneagent.data.llm.sse.toModelEvent
import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.auth.Credentials
import com.example.phoneagent.domain.auth.CredentialsProvider
import com.example.phoneagent.domain.auth.CredentialsResult
import com.example.phoneagent.domain.model.ModelEvent
import com.example.phoneagent.domain.model.ModelGateway
import com.example.phoneagent.domain.model.ModelRequest
import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.DefaultDispatcherProvider
import com.example.phoneagent.domain.platform.DispatcherProvider
import com.example.phoneagent.domain.platform.warn
import com.example.phoneagent.domain.storage.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "ResponsesGateway"
private const val AUTHORIZATION_HEADER = "Authorization"
private const val BEARER_PREFIX = "Bearer "
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val MAX_ATTEMPTS = 2
private const val DEFAULT_MODEL = "gpt-5.6-luna"
private val JSON_MEDIA_TYPE = "application/json".toMediaType()

/**
 * Implements [ModelGateway] against the Responses API (docs/backend-notes.md). A 401 is retried
 * exactly once after [CredentialsProvider.refreshAfterUnauthorized] — as plain sequential suspend
 * code, not an OkHttp Authenticator, because that would need `runBlocking` to call a suspend
 * function from OkHttp's synchronous callback, which architecture/patterns.md forbids in
 * production code (enforced by ArchitectureTest). Everything else (403/429/network) maps straight
 * to [ModelEvent.Failed] rather than being thrown, matching the port's contract.
 *
 * Uses `channelFlow`/`send` rather than `flow`/`emit`: the blocking OkHttp read has to run on
 * `Dispatchers.IO`, and plain `flow{}` forbids emitting from a dispatcher other than the
 * collector's ("flow exception transparency"); `channelFlow` decouples producer and collector for
 * exactly this case. The work runs in a `launch`ed child so `awaitClose` — which, unlike
 * `Job.invokeOnCompletion`, is guaranteed to run promptly on cancellation rather than waiting for
 * the (blocked-on-a-socket-read) job to reach a terminal state — can cancel the in-flight `Call`.
 */
public class ResponsesGateway(
    private val profile: BackendProfile,
    private val credentialsProvider: CredentialsProvider,
    private val settingsRepository: SettingsRepository,
    private val logger: AppLogger,
    private val client: OkHttpClient = OkHttpClient(),
    // Test seam: lets a test capture the Call OkHttp created, to assert it gets cancelled when
    // the collecting coroutine is (see docs/stages/3-model-client.md item 3.5).
    private val callFactory: Call.Factory = client,
    private val newSessionId: () -> String = { UUID.randomUUID().toString() },
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
) : ModelGateway {
    override fun stream(request: ModelRequest): Flow<ModelEvent> =
        channelFlow {
            val channel: SendChannel<ModelEvent> = this
            val activeCall = AtomicReference<Call?>(null)
            launch {
                try {
                    when (val ready = credentialsProvider.current()) {
                        is CredentialsResult.Ready -> channel.streamWithRetry(request, ready.credentials, activeCall)

                        CredentialsResult.SignedOut -> channel.send(ModelEvent.Failed(AgentError.SessionExpired))

                        // A refresh that failed for a temporary reason must not look like a lost session.
                        CredentialsResult.Unavailable -> channel.send(ModelEvent.Failed(AgentError.Offline))
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    logger.warn(TAG, "failed to start a model request", throwable = exception)
                    channel.send(ModelEvent.Failed(AgentError.Unexpected(exception.message ?: "unknown")))
                } finally {
                    channel.close()
                }
            }
            awaitClose { activeCall.get()?.cancel() }
        }

    private suspend fun SendChannel<ModelEvent>.streamWithRetry(
        request: ModelRequest,
        initialCredentials: Credentials,
        activeCall: AtomicReference<Call?>,
    ) {
        val model = settingsRepository.load().model ?: DEFAULT_MODEL
        val body = buildResponsesRequestBody(request, profile, model, newSessionId())
        var credentials = initialCredentials
        var failure: AgentError? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            if (attempt(body, credentials, activeCall) != AttemptOutcome.Unauthorized) return
            // The last attempt still got a 401: the credentials are not the problem any more.
            val refreshed = if (attempt < MAX_ATTEMPTS) credentialsProvider.refreshAfterUnauthorized() else CredentialsResult.SignedOut
            when (refreshed) {
                is CredentialsResult.Ready -> credentials = refreshed.credentials

                // The token could not be refreshed right now (no network): the session is intact, so this is a
                // temporary failure the user can retry, not an expired session.
                CredentialsResult.Unavailable -> failure = AgentError.Offline

                CredentialsResult.SignedOut -> failure = AgentError.SessionExpired
            }
            if (failure != null) break
        }
        failure?.let { send(ModelEvent.Failed(it)) }
    }

    private suspend fun SendChannel<ModelEvent>.attempt(
        body: JsonObject,
        credentials: Credentials,
        activeCall: AtomicReference<Call?>,
    ): AttemptOutcome {
        val call = callFactory.newCall(httpRequest(body, credentials))
        activeCall.set(call)
        return withContext(dispatchers.io) {
            try {
                val response = call.execute()
                try {
                    handle(response)
                } finally {
                    response.close()
                }
            } catch (e: IOException) {
                logger.warn(TAG, "model request network failure", throwable = e)
                send(ModelEvent.Failed(e.toAgentError()))
                AttemptOutcome.Done
            }
        }
    }

    private suspend fun SendChannel<ModelEvent>.handle(response: Response): AttemptOutcome {
        when {
            response.code == HTTP_UNAUTHORIZED -> {
                return AttemptOutcome.Unauthorized
            }

            !response.isSuccessful -> {
                // Only identifiers from the error (type/code/param), never the body itself: it can echo
                // chat text, and the redacting logger only strips token-shaped strings.
                logger.warn(
                    TAG,
                    "model request rejected",
                    fields = mapOf("status" to response.code.toString(), "error" to summarizeErrorBody(response.bodyText())),
                )
                send(ModelEvent.Failed(response.toAgentError()))
            }

            else -> {
                streamBody(response)
            }
        }
        return AttemptOutcome.Done
    }

    private suspend fun SendChannel<ModelEvent>.streamBody(response: Response) {
        val source = response.body.source()
        val parser = SseParser()
        val events =
            generateSequence { source.readUtf8Line() }
                .flatMap { line -> parser.feed("$line\n") }
                .mapNotNull { it.toModelEvent() }
        for (event in events) {
            send(event)
            if (event is ModelEvent.Completed || event is ModelEvent.Failed) break
        }
    }

    private fun httpRequest(
        body: JsonObject,
        credentials: Credentials,
    ): Request =
        Request
            .Builder()
            .url("${profile.baseUrl}/responses")
            .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX${credentials.accessToken}")
            .apply { credentials.accountId?.let { header("ChatGPT-Account-ID", it) } }
            .header("Accept", "text/event-stream")
            .post(Json.encodeToString(JsonObject.serializer(), body).toRequestBody(JSON_MEDIA_TYPE))
            .build()
}

private enum class AttemptOutcome {
    Done,
    Unauthorized,
}

private fun Response.bodyText(): String =
    try {
        body.string()
    } catch (_: IOException) {
        ""
    }

private fun Response.toAgentError(): AgentError =
    when (code) {
        HTTP_FORBIDDEN -> AgentError.Forbidden
        HTTP_TOO_MANY_REQUESTS -> AgentError.RateLimited(header("Retry-After")?.toLongOrNull())
        else -> AgentError.Server(code)
    }

private fun IOException.toAgentError(): AgentError =
    when (this) {
        is SocketTimeoutException -> AgentError.Timeout
        else -> AgentError.Offline
    }
