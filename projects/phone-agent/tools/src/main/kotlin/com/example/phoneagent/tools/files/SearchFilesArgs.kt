package com.example.phoneagent.tools.files

import kotlinx.serialization.Serializable

@Serializable
internal data class SearchFilesArgs(
    val query: String,
) {
    fun validationError(): SearchFilesValidationError? =
        when {
            query.isBlank() -> SearchFilesValidationError.EMPTY_QUERY
            query.length > MAX_QUERY_LENGTH -> SearchFilesValidationError.QUERY_TOO_LONG
            else -> null
        }

    internal companion object {
        const val MAX_QUERY_LENGTH = 200
    }
}

internal enum class SearchFilesValidationError {
    EMPTY_QUERY,
    QUERY_TOO_LONG,
}
