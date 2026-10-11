package com.example.phoneagent.domain.agent

public sealed interface AgentError {
    public data object SessionExpired : AgentError

    public data object Forbidden : AgentError

    public data object Offline : AgentError

    public data object Timeout : AgentError

    public data class RateLimited(
        public val retryAfterSeconds: Long?,
    ) : AgentError

    public data object ProtocolChanged : AgentError

    public data class Server(
        public val code: Int,
    ) : AgentError

    public data class ToolFailure(
        public val reason: String,
    ) : AgentError

    public data object StepLimit : AgentError

    /** The app was closed before the reply for the last question arrived. */
    public data object Interrupted : AgentError

    public data object Cancelled : AgentError

    public data class Unexpected(
        public val reason: String,
    ) : AgentError
}
