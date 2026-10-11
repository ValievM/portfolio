package com.example.phoneagent.tools.files

/** User-facing card copy and model-facing protocol text for `search_files`. */
internal object SearchFilesCopy {
    const val SPEC_DESCRIPTION: String =
        "Search the Android media index for shared files whose display name contains the query. " +
            "Return only file metadata; never read file contents. Results are limited to media " +
            "currently accessible to the app, and some downloads from other apps may require " +
            "the user to select a folder separately."
    const val QUERY_SCHEMA_DESCRIPTION: String = "Non-empty substring of a file name, up to 200 characters."

    const val CARD_TITLE: String = "Поиск файлов"
    const val CARD_SUMMARY: String = "Найти совпадения в доступном медиаиндексе"
    val CARD_DETAILS: List<String> =
        listOf(
            "Возвращаются только метаданные, содержимое не читается",
            "Часть загрузок других приложений может быть недоступна",
        )
    const val PERMISSION_DENIED: String = "Media access was not granted; ask the user to allow file search access."
    const val INVALID_ARGUMENTS: String = "Invalid search_files arguments: query must be a string."

    fun cardSummary(query: String): String = "Найти файлы по запросу «$query» в доступном медиаиндексе"

    fun validationError(error: SearchFilesValidationError): String =
        when (error) {
            SearchFilesValidationError.EMPTY_QUERY -> "query must not be empty or blank"
            SearchFilesValidationError.QUERY_TOO_LONG -> "query must be at most ${SearchFilesArgs.MAX_QUERY_LENGTH} characters"
        }

    /** Model-facing reason: fixed text on purpose - the provider exception may embed URIs/paths. */
    const val SEARCH_FAILED: String = "MediaStore search failed: the provider reported an error."

    fun searchFailed(): String = SEARCH_FAILED
}
