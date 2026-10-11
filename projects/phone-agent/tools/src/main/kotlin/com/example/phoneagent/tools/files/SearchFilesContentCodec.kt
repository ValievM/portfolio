package com.example.phoneagent.tools.files

import com.example.phoneagent.domain.tools.ToolContent
import com.example.phoneagent.domain.tools.ToolFile
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.net.URI

/** Owns conversion between the legacy search result text and typed file metadata. */
internal object SearchFilesContentCodec {
    fun restoreContent(outputText: String): ToolContent.FilesFound {
        val root =
            try {
                Json.parseToJsonElement(outputText) as? JsonObject
            } catch (_: SerializationException) {
                null
            }
        val files = (root?.get(ITEMS_KEY) as? JsonArray)?.mapNotNull { (it as? JsonObject)?.toToolFile() }.orEmpty()
        return ToolContent.FilesFound(files)
    }

    fun toToolFile(metadata: SearchFileMetadata): ToolFile? {
        val file = ToolFile(metadata.name, metadata.uri, metadata.size.coerceAtLeast(0L), metadata.mimeType)
        return file.takeIf { metadata.name.isNotBlank() && metadata.uri.hasContentUriAuthority() }
    }

    private fun JsonObject.toToolFile(): ToolFile? {
        val name = this[NAME_KEY].stringOrNull()
        val uri = this[URI_KEY].stringOrNull()
        val sizeElement = this[SIZE_KEY]
        val sizePrimitive = sizeElement as? JsonPrimitive
        val size = sizePrimitive?.longOrNull
        val mimeElement = this[MIME_TYPE_KEY]
        val mime = mimeElement.stringOrNull()
        val uriIsSafe = uri?.hasContentUriAuthority() == true
        val sizeIsValid =
            sizeElement == null || sizeElement == JsonNull ||
                (sizePrimitive != null && !sizePrimitive.isString && size != null && size >= 0L)
        val mimeIsValid = mimeElement == null || mimeElement == JsonNull || mime != null
        val file = ToolFile(name.orEmpty(), uri.orEmpty(), size, mime)
        return file.takeIf {
            name?.isNotBlank() == true &&
                uri?.isNotBlank() == true &&
                uriIsSafe &&
                sizeIsValid &&
                mimeIsValid
        }
    }

    private fun JsonElement?.stringOrNull(): String? {
        val primitive = this as? JsonPrimitive ?: return null
        return if (primitive.isString) primitive.contentOrNull else null
    }

    private fun String.hasContentUriAuthority(): Boolean =
        runCatching {
            val parsed = URI(this)
            parsed.scheme.equals(CONTENT_SCHEME, ignoreCase = true) && parsed.rawAuthority != null
        }.getOrDefault(false)

    private const val ITEMS_KEY = "items"
    private const val NAME_KEY = "name"
    private const val URI_KEY = "uri"
    private const val SIZE_KEY = "size"
    private const val MIME_TYPE_KEY = "mimeType"
    private const val CONTENT_SCHEME = "content"
}
