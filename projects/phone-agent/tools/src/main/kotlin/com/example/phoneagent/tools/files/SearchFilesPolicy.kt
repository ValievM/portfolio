package com.example.phoneagent.tools.files

import com.example.phoneagent.domain.tools.ToolContent
import com.example.phoneagent.domain.tools.ToolFile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object SearchFilesLimits {
    const val TIMEOUT_MILLIS: Long = 5_000
    const val MAX_RESULTS: Int = 20
    const val MAX_OUTPUT_BYTES: Int = 3_500
}

internal object SearchFilesMetadataCaps {
    const val NAME: Int = 255
    const val PATH: Int = 1_024
    const val MIME_TYPE: Int = 128
    const val URI: Int = 2_048
}

internal data class SearchFileMetadata(
    val name: String,
    val uri: String,
    val path: String,
    val size: Long,
    val dateModified: Long?,
    val mimeType: String?,
)

internal data class SearchFilesResult(
    val items: List<SearchFileMetadata>,
    val truncated: Boolean,
    /** True when some MediaStore collections were inaccessible (partial media access). */
    val partialAccess: Boolean = false,
)

internal data class SearchFilesOutput(
    val text: String,
    val content: ToolContent.FilesFound,
)

internal object SearchFilesPolicy {
    fun process(
        records: List<SearchFileMetadata>,
        sourceIncomplete: Boolean = false,
        partialAccess: Boolean = false,
    ): SearchFilesResult {
        val newestByIdentity = linkedMapOf<Pair<String, Long>, SearchFileMetadata>()
        records.forEach { record ->
            if (!record.uri.isSafeContentUri()) return@forEach
            val key = record.name to record.size
            val current = newestByIdentity[key]
            if (current == null || record.dateModified.isNewerThan(current.dateModified)) {
                newestByIdentity[key] = record
            }
        }

        val sorted =
            newestByIdentity.values
                .map(::clipMetadata)
                .sortedWith(
                    compareByDescending<SearchFileMetadata> { it.dateModified ?: Long.MIN_VALUE }
                        .thenBy { it.name }
                        .thenBy { it.uri },
                )
        return SearchFilesResult(
            items = sorted.take(SearchFilesLimits.MAX_RESULTS),
            truncated = sourceIncomplete || sorted.size > SearchFilesLimits.MAX_RESULTS,
            partialAccess = partialAccess,
        )
    }

    fun toJson(result: SearchFilesResult): String = toOutput(result).text

    fun toOutput(result: SearchFilesResult): SearchFilesOutput {
        val items = mutableListOf<kotlinx.serialization.json.JsonObject>()
        val files = mutableListOf<ToolFile>()
        var truncated = result.truncated
        for (item in result.items) {
            val candidate = item.toJsonObject()
            val proposedItems = items + candidate
            val proposedJson = serialize(proposedItems, truncated = true, partialAccess = true)
            if (proposedJson.toByteArray(Charsets.UTF_8).size > SearchFilesLimits.MAX_OUTPUT_BYTES) {
                truncated = true
                break
            }
            items += candidate
            SearchFilesContentCodec.toToolFile(item)?.let(files::add)
        }
        return SearchFilesOutput(
            text = serialize(items, truncated || items.size < result.items.size, partialAccess = result.partialAccess),
            content = ToolContent.FilesFound(files),
        )
    }

    private fun serialize(
        items: List<kotlinx.serialization.json.JsonObject>,
        truncated: Boolean,
        partialAccess: Boolean,
    ): String =
        buildJsonObject {
            put("items", JsonArray(items))
            put("truncated", truncated)
            put("partialAccess", partialAccess)
        }.toString()

    private fun SearchFileMetadata.toJsonObject() =
        buildJsonObject {
            put("name", name)
            put("uri", uri)
            put("path", path)
            put("size", size)
            put("dateModified", dateModified?.let(::JsonPrimitive) ?: JsonNull)
            put("mimeType", mimeType?.let(::JsonPrimitive) ?: JsonNull)
        }

    private fun clipMetadata(record: SearchFileMetadata): SearchFileMetadata {
        val clippedName = record.name.takeCodePoints(SearchFilesMetadataCaps.NAME)
        val clippedPath = record.path.takeCodePoints(SearchFilesMetadataCaps.PATH)
        return record.copy(
            name = clippedName,
            path = clippedPath.ifEmpty { clippedName },
            size = record.size.coerceAtLeast(0L),
            mimeType = record.mimeType?.takeCodePoints(SearchFilesMetadataCaps.MIME_TYPE),
        )
    }

    private fun String.isSafeContentUri(): Boolean = startsWith("content://") && length <= SearchFilesMetadataCaps.URI

    private fun Long?.isNewerThan(other: Long?): Boolean =
        when {
            this == null -> false
            other == null -> true
            else -> this > other
        }

    private fun String.takeCodePoints(limit: Int): String {
        val count = codePointCount(0, length)
        val end = offsetByCodePoints(0, minOf(count, limit))
        return substring(0, end)
    }
}
