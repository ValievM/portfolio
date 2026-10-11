package com.example.phoneagent.tools.files

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFilesPolicyTest {
    @Test
    fun `blank query is invalid and exactly 200 characters is valid`() {
        assertEquals(SearchFilesValidationError.EMPTY_QUERY, SearchFilesArgs(" \t\n").validationError())
        assertEquals(null, SearchFilesArgs("x".repeat(200)).validationError())
    }

    @Test
    fun `query over 200 characters is invalid`() {
        assertEquals(SearchFilesValidationError.QUERY_TOO_LONG, SearchFilesArgs("x".repeat(201)).validationError())
    }

    @Test
    fun `deduplicates exact name and size while keeping the newest record`() {
        val duplicateOld = hit("report.pdf", size = 10, dateModified = 30, uri = "content://old")
        val duplicateNew = hit("report.pdf", size = 10, dateModified = 70, uri = "content://new")
        val differentSize = hit("report.pdf", size = 11, dateModified = 50)

        val result = SearchFilesPolicy.process(listOf(duplicateOld, differentSize, duplicateNew))

        assertEquals(listOf("content://new", "content://different"), result.items.map { it.uri })
    }

    @Test
    fun `orders newest dates first and places unknown dates last`() {
        val result =
            SearchFilesPolicy.process(
                listOf(
                    hit("unknown.txt", dateModified = null),
                    hit("older.txt", dateModified = 10),
                    hit("newer.txt", dateModified = 20),
                ),
            )

        assertEquals(listOf("newer.txt", "older.txt", "unknown.txt"), result.items.map { it.name })
    }

    @Test
    fun `caps results at 20 and marks either overflow or timeout as truncated`() {
        val records = (0 until 21).map { hit("file-$it", dateModified = it.toLong()) }

        val overflow = SearchFilesPolicy.process(records)
        val timedOut = SearchFilesPolicy.process(records.take(2), sourceIncomplete = true)

        assertEquals(20, overflow.items.size)
        assertTrue(overflow.truncated)
        assertTrue(timedOut.truncated)
    }

    @Test
    fun `clips display metadata without corrupting the content uri`() {
        val result =
            SearchFilesPolicy.process(
                listOf(
                    hit(
                        name = "н".repeat(SearchFilesMetadataCaps.NAME + 10),
                        path = "п".repeat(SearchFilesMetadataCaps.PATH + 10),
                        mimeType = "m".repeat(SearchFilesMetadataCaps.MIME_TYPE + 10),
                    ),
                ),
            )
        val item = result.items.single()

        assertEquals(SearchFilesMetadataCaps.NAME, item.name.length)
        assertEquals(SearchFilesMetadataCaps.PATH, item.path.length)
        assertEquals(SearchFilesMetadataCaps.MIME_TYPE, item.mimeType?.length)
        assertEquals("content://different", item.uri)
    }

    @Test
    fun `serializes valid escaped JSON with the UI file hit contract`() {
        val result = SearchFilesPolicy.process(listOf(hit(name = "quote \" and slash \\")))

        val root = Json.parseToJsonElement(SearchFilesPolicy.toJson(result)).jsonObject
        val item =
            root
                .getValue("items")
                .jsonArray
                .single()
                .jsonObject

        assertFalse(
            root
                .getValue("truncated")
                .jsonPrimitive.content
                .toBoolean(),
        )
        assertEquals("quote \" and slash \\", item.getValue("name").jsonPrimitive.content)
        assertEquals("content://different", item.getValue("uri").jsonPrimitive.content)
        assertEquals("folder/quote.pdf", item.getValue("path").jsonPrimitive.content)
        assertEquals("12", item.getValue("size").jsonPrimitive.content)
        assertEquals("123", item.getValue("dateModified").jsonPrimitive.content)
        assertEquals("application/pdf", item.getValue("mimeType").jsonPrimitive.content)
    }

    @Test
    fun `rejects records without a safe content uri`() {
        val result =
            SearchFilesPolicy.process(
                listOf(
                    hit(name = "secret", uri = "file:///secret"),
                    hit(name = "oversized", uri = "content://provider/" + "x".repeat(SearchFilesMetadataCaps.URI)),
                ),
            )

        assertTrue(result.items.isEmpty())
    }

    @Test
    fun `partial access is reported separately from truncation`() {
        val result = SearchFilesPolicy.process(listOf(hit(name = "seen")), partialAccess = true)

        val json = Json.parseToJsonElement(SearchFilesPolicy.toJson(result)).jsonObject
        assertEquals("true", json.getValue("partialAccess").toString())
        assertEquals("false", json.getValue("truncated").toString())
    }

    @Test
    fun `serializes absent optional metadata as JSON null`() {
        val result = SearchFilesPolicy.process(listOf(hit(name = "voice-note", dateModified = null, mimeType = null)))
        val item =
            Json
                .parseToJsonElement(SearchFilesPolicy.toJson(result))
                .jsonObject
                .getValue("items")
                .jsonArray
                .single()
                .jsonObject

        assertEquals("null", item.getValue("dateModified").toString())
        assertEquals("null", item.getValue("mimeType").toString())
    }

    @Test
    fun `keeps serialized result below the shared tool output budget`() {
        val records =
            (0 until SearchFilesLimits.MAX_RESULTS).map { index ->
                hit(
                    name = "файл-$index-".repeat(30),
                    path = "папка/".repeat(100),
                    uri = "content://provider/item/$index",
                    mimeType = "application/octet-stream",
                    dateModified = index.toLong(),
                )
            }

        val json = SearchFilesPolicy.toJson(SearchFilesPolicy.process(records))
        val root = Json.parseToJsonElement(json).jsonObject

        assertTrue(json.toByteArray(Charsets.UTF_8).size <= SearchFilesLimits.MAX_OUTPUT_BYTES)
        assertTrue(
            root
                .getValue("truncated")
                .jsonPrimitive.content
                .toBoolean(),
        )
    }

    private fun hit(
        name: String,
        size: Long = 12,
        dateModified: Long? = 123,
        uri: String = "content://different",
        path: String = "folder/quote.pdf",
        mimeType: String? = "application/pdf",
    ) = SearchFileMetadata(name, uri, path, size, dateModified, mimeType)
}
