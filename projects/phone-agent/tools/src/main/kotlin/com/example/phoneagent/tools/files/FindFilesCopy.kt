package com.example.phoneagent.tools.files

/**
 * All user-facing and model-facing text for `find_files` in one place, kept out of the tool's
 * logic files. Values are unchanged from where they used to live inline — this is purely a
 * relocation so a future localization pass has one file to change per tool instead of a hunt
 * through the codebase.
 */
internal object FindFilesCopy {
    const val SPEC_DESCRIPTION: String =
        "Search the folder the user pre-selected in the app settings for files whose " +
            "name contains the query. Returns up to 20 matches as name and path relative " +
            "to that folder. Only names are read; file contents are never opened."
    const val QUERY_SCHEMA_DESCRIPTION: String = "Case-insensitive substring of a file name to look for."

    const val CARD_TITLE: String = "Поиск файлов"
    const val CARD_SUMMARY_NO_QUERY: String = "Найти файлы в выбранной пользователем папке"
    val CARD_DETAILS: List<String> = listOf("Показываются только имена файлов, содержимое не читается")

    const val QUERY_BLANK: String = "query must not be empty or blank"
    const val REASON_NOT_SELECTED: String = "Search folder is not selected: the user needs to pick one in the app settings"
    const val NO_MATCHES: String = "No files matching the query were found in the selected folder."
    const val LIMIT_DEPTH: String = "Search stopped early: depth limit of 6 reached."
    const val LIMIT_NODES: String = "Search stopped early: scan limit of 5000 nodes reached."
    const val LIMIT_TIME: String = "Search stopped early: 5-second time limit reached."
    const val LIMIT_RESULTS: String = "Result list capped at 20 matches; more may exist."

    fun cardSummaryWithQuery(query: String): String = "Найти файлы по запросу «$query» в выбранной папке"

    fun queryTooLong(
        length: Int,
        maxLength: Int,
    ): String = "query must be at most $maxLength characters, got $length"

    fun invalidArguments(reason: String?): String = "Invalid find_files arguments: $reason"

    /** Model-facing reason: fixed text on purpose - the exception may embed paths/URIs. */
    const val FILE_SYSTEM_QUERY_FAILED: String = "File system query failed: the folder could not be read."

    fun fileSystemQueryFailed(): String = FILE_SYSTEM_QUERY_FAILED

    fun matchesFoundHeader(count: Int): String = "Found $count matching item(s) in the selected folder:"

    fun matchLine(
        name: String,
        relativePath: String,
    ): String = "- $name ($relativePath)"
}
