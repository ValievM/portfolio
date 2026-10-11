package com.example.phoneagent.tools.files

import com.example.phoneagent.domain.interaction.Capability
import com.example.phoneagent.domain.interaction.CapabilityResult
import com.example.phoneagent.domain.interaction.UserInteraction
import com.example.phoneagent.domain.platform.DefaultDispatcherProvider
import com.example.phoneagent.domain.platform.DispatcherProvider
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.Tool
import com.example.phoneagent.domain.tools.ToolContent
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

internal class SearchFilesTool
    @Inject
    constructor(
        private val userInteraction: UserInteraction,
        private val mediaStoreSearch: MediaStoreSearch,
        private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
    ) : Tool {
        override val spec: ToolSpec =
            ToolSpec(
                name = "search_files",
                description = SearchFilesCopy.SPEC_DESCRIPTION,
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
                                        put("description", SearchFilesCopy.QUERY_SCHEMA_DESCRIPTION)
                                        put("minLength", 1)
                                        put("maxLength", SearchFilesArgs.MAX_QUERY_LENGTH)
                                    },
                                )
                            },
                        )
                        put("required", JsonArray(listOf(JsonPrimitive("query"))))
                    },
                risk = Risk.SAFE,
                requiredCapabilities = setOf(Capability.FILES),
            )

        override fun deniedMessage(capability: Capability): String = SearchFilesCopy.PERMISSION_DENIED

        override fun describe(args: JsonObject): CallDescription {
            val query = (parseAndValidate(args) as? ParsedSearchArgs.Valid)?.query?.takeIf(String::isNotBlank)
            return CallDescription(
                title = SearchFilesCopy.CARD_TITLE,
                summary = query?.let(SearchFilesCopy::cardSummary) ?: SearchFilesCopy.CARD_SUMMARY,
                details = SearchFilesCopy.CARD_DETAILS,
            )
        }

        override fun restoreContent(outputText: String): ToolContent? = SearchFilesContentCodec.restoreContent(outputText)

        override suspend fun execute(
            args: JsonObject,
            context: ToolContext,
        ): ToolResult =
            when (val parsed = parseAndValidate(args)) {
                is ParsedSearchArgs.Invalid -> ToolResult.Failed(parsed.reason)
                is ParsedSearchArgs.Valid -> searchFiles(parsed.query, context)
            }

        private suspend fun searchFiles(
            query: String,
            context: ToolContext,
        ): ToolResult {
            if (userInteraction.ensure(Capability.FILES) is CapabilityResult.Denied) {
                return ToolResult.Failed(SearchFilesCopy.PERMISSION_DENIED)
            }
            val startedAt = context.clock.nowEpochMillis()
            var budgetReached = false

            fun shouldContinue(): Boolean {
                if (context.clock.nowEpochMillis() - startedAt >= SearchFilesLimits.TIMEOUT_MILLIS) {
                    budgetReached = true
                    return false
                }
                return true
            }

            return try {
                val searched = withContext(dispatchers.io) { mediaStoreSearch.search(query, ::shouldContinue) }
                if (!shouldContinue()) budgetReached = true
                val result =
                    SearchFilesPolicy.process(
                        searched.records,
                        sourceIncomplete = searched.truncated || budgetReached,
                        partialAccess = searched.inaccessible,
                    )
                val output = SearchFilesPolicy.toOutput(result)
                ToolResult.Success(output.text, output.content)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (expected: Exception) {
                // Fixed reason on purpose: the provider exception may embed URIs/paths.
                ToolResult.Failed(SearchFilesCopy.searchFailed())
            }
        }

        private companion object {
            val json = Json { ignoreUnknownKeys = true }

            fun parseAndValidate(args: JsonObject): ParsedSearchArgs =
                try {
                    val parsed = json.decodeFromJsonElement(SearchFilesArgs.serializer(), args)
                    when (val error = parsed.validationError()) {
                        null -> ParsedSearchArgs.Valid(parsed.query)
                        else -> ParsedSearchArgs.Invalid(SearchFilesCopy.validationError(error))
                    }
                } catch (_: SerializationException) {
                    ParsedSearchArgs.Invalid(SearchFilesCopy.INVALID_ARGUMENTS)
                }
        }
    }

private sealed interface ParsedSearchArgs {
    data class Valid(
        val query: String,
    ) : ParsedSearchArgs

    data class Invalid(
        val reason: String,
    ) : ParsedSearchArgs
}
