package com.example.phoneagent.ui.designsystem.components

import java.net.URI

data class FileHit(
    val name: String,
    val uri: String,
    val size: Long?,
    val mime: String?,
) {
    fun hasContentUriScheme(): Boolean =
        runCatching {
            val parsed = URI(uri)
            parsed.scheme.equals("content", ignoreCase = true) && parsed.rawAuthority != null
        }.getOrDefault(false)
}
