package com.example.phoneagent.ui.designsystem.components

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** What to do with a link that appeared in model output (docs/design/a11y-security.md). */
sealed interface LinkDecision {
    /** Ask the user first; [host] is the real domain (and port), never a decoy from the link text. */
    data class Confirm(
        val url: String,
        val host: String,
    ) : LinkDecision

    /** Never opened: anything that is not a well-formed https link with a host. */
    data object Blocked : LinkDecision
}

private const val HTTPS = "https"
private const val NO_PORT = -1

/**
 * Model output is untrusted, so a link is only ever opened after a tap on a confirmation showing
 * the actual domain, and only for https. The domain comes from parsing the URL, not from the text.
 */
fun evaluateLink(rawUrl: String): LinkDecision {
    val url = rawUrl.trim()
    val uri =
        try {
            URI(url)
        } catch (_: URISyntaxException) {
            return LinkDecision.Blocked
        }
    val host = uri.host?.takeIf { it.isNotBlank() }
    return if (uri.scheme.equals(HTTPS, ignoreCase = true) && host != null) {
        val shownHost = host.lowercase(Locale.ROOT)
        LinkDecision.Confirm(url = url, host = if (uri.port == NO_PORT) shownHost else "$shownHost:${uri.port}")
    } else {
        LinkDecision.Blocked
    }
}
