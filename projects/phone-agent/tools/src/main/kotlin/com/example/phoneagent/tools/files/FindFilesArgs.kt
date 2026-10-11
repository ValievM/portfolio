package com.example.phoneagent.tools.files

import kotlinx.serialization.Serializable

/** Arguments of a find_files call, decoded from the model's JSON by [FindFilesTool]. */
@Serializable
internal data class FindFilesArgs(
    /** Substring to look for in file and folder names; matched as plain data, case-insensitively. */
    val query: String,
) {
    /** Null when the query is usable; otherwise a short reason for the model to correct itself. */
    fun validationError(): String? =
        when {
            query.isBlank() -> {
                FindFilesCopy.QUERY_BLANK
            }

            query.length > MAX_QUERY_LENGTH -> {
                FindFilesCopy.queryTooLong(query.length, MAX_QUERY_LENGTH)
            }

            else -> {
                null
            }
        }

    internal companion object {
        // Generous for a file-name substring (file names are rarely longer than 255 chars).
        const val MAX_QUERY_LENGTH = 200
    }
}
