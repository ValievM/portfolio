package com.example.phoneagent.tools.files

import com.example.phoneagent.domain.platform.DefaultDispatcherProvider
import com.example.phoneagent.domain.platform.DispatcherProvider
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.Tool
import com.example.phoneagent.domain.tools.ToolContext
import com.example.phoneagent.domain.tools.ToolResult
import com.example.phoneagent.domain.tools.ToolSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

/**
 * `find_files` tool: case-insensitive name search inside the folder the user pre-selected in
 * the app settings. SAFE risk — it only reports names and relative paths, never file contents,
 * and never leaves the selected subtree (enforced by [SafTreeWalker]). Expected failures come
 * back as [ToolResult.Failed] with a reason, not exceptions.
 */
internal class FindFilesTool
    @Inject
    constructor(
        private val selectedFolder: SelectedFolder,
        // Default keeps the existing manual test constructions compiling; DI injects the bound
        // provider (see the app's AgentModule).
        private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
    ) : Tool {
        override val spec: ToolSpec =
            ToolSpec(
                name = NAME,
                description = FindFilesCopy.SPEC_DESCRIPTION,
                inputSchema =
                    buildJsonObject {
                        put("type", "object")
                        put(
                            "properties",
                            buildJsonObject {
                                put(
                                    "query",
                                    buildJsonObject {
                                        put("type", "string")
                                        put("description", FindFilesCopy.QUERY_SCHEMA_DESCRIPTION)
                                    },
                                )
                            },
                        )
                        put("required", JsonArray(listOf(JsonPrimitive("query"))))
                    },
                risk = Risk.SAFE,
            )

        override fun describe(args: JsonObject): CallDescription {
            val query = args.queryOrNull()
            return CallDescription(
                title = FindFilesCopy.CARD_TITLE,
                summary = query?.let(FindFilesCopy::cardSummaryWithQuery) ?: FindFilesCopy.CARD_SUMMARY_NO_QUERY,
                details = FindFilesCopy.CARD_DETAILS,
            )
        }

        override suspend fun execute(
            args: JsonObject,
            context: ToolContext,
        ): ToolResult {
            val parsed = parseArgs(args)
            return when (parsed) {
                is ParsedArgs.Invalid -> {
                    ToolResult.Failed(parsed.reason)
                }

                is ParsedArgs.Valid -> {
                    // ContentResolver calls block their thread, and the agent loop runs on Main:
                    // both the folder lookup and the walk must leave the caller's dispatcher.
                    when (val root = withContext(dispatchers.io) { selectedFolder.root() }) {
                        null -> ToolResult.Failed(FindFilesCopy.REASON_NOT_SELECTED)
                        else -> searchSafely(root, parsed.query, context)
                    }
                }
            }
        }

        private suspend fun searchSafely(
            root: DocumentNode,
            query: String,
            context: ToolContext,
        ): ToolResult =
            try {
                val result =
                    withContext(dispatchers.io) {
                        SafTreeWalker(context.clock).search(root, query)
                    }
                ToolResult.Success(result.toModelText())
            } catch (cancellation: CancellationException) {
                // A Stop during the walk must stay a cancellation, not become a failure report.
                throw cancellation
            } catch (expected: Exception) {
                // Fixed reason on purpose: the exception may embed paths/URIs.
                ToolResult.Failed(FindFilesCopy.fileSystemQueryFailed())
            }

        private companion object {
            const val NAME = "find_files"
        }
    }

private sealed interface ParsedArgs {
    data class Valid(
        val query: String,
    ) : ParsedArgs

    data class Invalid(
        val reason: String,
    ) : ParsedArgs
}

private fun parseArgs(args: JsonObject): ParsedArgs =
    try {
        val parsed = json.decodeFromJsonElement(FindFilesArgs.serializer(), args)
        parsed
            .validationError()
            ?.let { ParsedArgs.Invalid(FindFilesCopy.invalidArguments(it)) }
            ?: ParsedArgs.Valid(parsed.query)
    } catch (expected: SerializationException) {
        ParsedArgs.Invalid(FindFilesCopy.invalidArguments(expected.message))
    }

private fun WalkResult.toModelText(): String =
    if (matches.isEmpty()) {
        FindFilesCopy.NO_MATCHES
    } else {
        buildString {
            appendLine(FindFilesCopy.matchesFoundHeader(matches.size))
            for (match in matches) {
                appendLine(FindFilesCopy.matchLine(match.name, match.relativePath))
            }
            if (WalkLimit.DEPTH in limitsHit) appendLine(FindFilesCopy.LIMIT_DEPTH)
            if (WalkLimit.NODES in limitsHit) appendLine(FindFilesCopy.LIMIT_NODES)
            if (WalkLimit.TIME in limitsHit) appendLine(FindFilesCopy.LIMIT_TIME)
            if (WalkLimit.RESULTS in limitsHit) appendLine(FindFilesCopy.LIMIT_RESULTS)
        }
    }

private fun JsonObject.queryOrNull(): String? =
    try {
        json.decodeFromJsonElement(FindFilesArgs.serializer(), this).query.takeIf { it.isNotBlank() }
    } catch (expected: SerializationException) {
        null
    }

private val json = Json { ignoreUnknownKeys = true }
