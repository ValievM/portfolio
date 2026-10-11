package com.example.phoneagent.ui.chat

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.phoneagent.R
import com.example.phoneagent.domain.agent.AgentError

/** Where an error is shown (docs/design/components.md): system states above the composer, the rest in the feed. */
internal enum class ErrorPlacement {
    StatusBanner,
    Inline,
}

/**
 * How one [AgentError] is presented. [argument] fills the `%1$d` of [messageRes] when it has one.
 * Raw reasons never reach the screen: they can come from tools or the model, i.e. untrusted data.
 */
internal data class ErrorPresentation(
    @param:StringRes val messageRes: Int,
    val placement: ErrorPlacement,
    val retryable: Boolean,
    val argument: Long? = null,
)

/**
 * The single place that decides how every error looks. The `when` is exhaustive without `else`, so
 * a new [AgentError] does not compile until it has a message. `null` means "not an error to show".
 */
internal fun AgentError.toPresentation(): ErrorPresentation? =
    when (this) {
        AgentError.Offline -> {
            ErrorPresentation(R.string.error_offline, ErrorPlacement.StatusBanner, retryable = true)
        }

        AgentError.SessionExpired -> {
            ErrorPresentation(R.string.error_session_expired, ErrorPlacement.StatusBanner, retryable = false)
        }

        AgentError.Forbidden -> {
            ErrorPresentation(R.string.error_forbidden, ErrorPlacement.Inline, retryable = false)
        }

        AgentError.Timeout -> {
            ErrorPresentation(R.string.error_timeout, ErrorPlacement.Inline, retryable = true)
        }

        is AgentError.RateLimited -> {
            if (retryAfterSeconds == null) {
                ErrorPresentation(R.string.error_rate_limited, ErrorPlacement.Inline, retryable = true)
            } else {
                ErrorPresentation(
                    R.string.error_rate_limited_after,
                    ErrorPlacement.Inline,
                    retryable = true,
                    argument = retryAfterSeconds,
                )
            }
        }

        is AgentError.Server -> {
            ErrorPresentation(R.string.error_server, ErrorPlacement.Inline, retryable = true, argument = code.toLong())
        }

        AgentError.ProtocolChanged -> {
            ErrorPresentation(R.string.error_protocol_changed, ErrorPlacement.Inline, retryable = false)
        }

        AgentError.StepLimit -> {
            ErrorPresentation(R.string.error_step_limit, ErrorPlacement.Inline, retryable = false)
        }

        is AgentError.ToolFailure -> {
            ErrorPresentation(R.string.error_tool_failure, ErrorPlacement.Inline, retryable = true)
        }

        is AgentError.Unexpected -> {
            ErrorPresentation(R.string.error_generic, ErrorPlacement.Inline, retryable = true)
        }

        AgentError.Interrupted -> {
            ErrorPresentation(R.string.error_interrupted, ErrorPlacement.Inline, retryable = true)
        }

        AgentError.Cancelled -> {
            null
        }
    }

/** The message text, with the number filled in where the message has one. */
@Composable
internal fun ErrorPresentation.text(): String = if (argument == null) stringResource(messageRes) else stringResource(messageRes, argument)
