package com.example.phoneagent.data.llm

import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.domain.auth.Credentials
import com.example.phoneagent.domain.auth.CredentialsResult
import com.example.phoneagent.domain.contracts.ModelGatewayContract
import com.example.phoneagent.domain.conversation.ToolCall
import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeCredentialsProvider
import com.example.phoneagent.domain.fakes.FakeSettingsRepository
import com.example.phoneagent.domain.model.ModelGateway
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import java.util.concurrent.TimeUnit

/** The real Responses gateway, driven by a local test server, obeys the same contract as the fake (see FakeModelGatewayContractTest). */
class ResponsesGatewayContractTest : ModelGatewayContract() {
    private val servers = mutableListOf<MockWebServer>()

    @After
    fun stopServers() = servers.forEach(MockWebServer::close)

    override fun replying(chunks: List<String>): ModelGateway {
        val body =
            chunks.joinToString("") { chunk -> "event: response.output_text.delta\ndata: {\"delta\":${JsonPrimitive(chunk)}}\n\n" } +
                "event: response.completed\ndata: {\"response\":{\"usage\":null}}\n\n"
        return gatewayAnswering(sse(body), sse(body))
    }

    override fun callingTool(call: ToolCall): ModelGateway {
        val arguments = JsonPrimitive(call.arguments.toString())
        val body =
            "event: response.output_item.done\n" +
                "data: {\"item\":{\"id\":\"${call.itemId.value}\",\"call_id\":\"${call.callId.value}\",\"type\":\"function_call\"," +
                "\"name\":\"${call.name}\",\"arguments\":$arguments}}\n\n" +
                "event: response.completed\ndata: {\"response\":{\"usage\":null}}\n\n"
        return gatewayAnswering(sse(body))
    }

    override fun failingWith(error: AgentError): ModelGateway =
        when (error) {
            // The backend is not reachable right now: the session is fine but the token cannot be refreshed.
            AgentError.Offline -> gatewayAnswering(credentials = FakeCredentialsProvider(CredentialsResult.Unavailable))

            AgentError.SessionExpired -> gatewayAnswering(credentials = FakeCredentialsProvider(CredentialsResult.SignedOut))

            else -> gatewayAnswering(MockResponse.Builder().code(SERVER_ERROR).build())
        }

    private fun gatewayAnswering(
        vararg responses: MockResponse,
        credentials: FakeCredentialsProvider = FakeCredentialsProvider(CredentialsResult.Ready(Credentials("token", null))),
    ): ResponsesGateway {
        val server = MockWebServer().also { it.start() }
        servers += server
        responses.forEach(server::enqueue)
        return ResponsesGateway(
            profile = BackendProfile(baseUrl = server.url("/backend-api/codex").toString().removeSuffix("/")),
            credentialsProvider = credentials,
            settingsRepository = FakeSettingsRepository(),
            logger = FakeAppLogger(),
            client = OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).build(),
            newSessionId = { "session-1" },
        )
    }

    private fun sse(body: String): MockResponse =
        MockResponse
            .Builder()
            .addHeader("Content-Type", "text/event-stream")
            .body(body)
            .build()

    private companion object {
        const val SERVER_ERROR = 500
    }
}
