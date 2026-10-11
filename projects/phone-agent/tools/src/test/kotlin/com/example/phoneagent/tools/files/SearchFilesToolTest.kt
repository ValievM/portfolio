package com.example.phoneagent.tools.files

import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import com.example.phoneagent.domain.fakes.FakeDispatcherProvider
import com.example.phoneagent.domain.fakes.FakeUserInteraction
import com.example.phoneagent.domain.interaction.Capability
import com.example.phoneagent.domain.interaction.CapabilityResult
import com.example.phoneagent.domain.platform.DispatcherProvider
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.ToolContent
import com.example.phoneagent.domain.tools.ToolContext
import com.example.phoneagent.domain.tools.ToolFile
import com.example.phoneagent.domain.tools.ToolResult
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

class SearchFilesToolTest {
    @Test
    fun `requires files capability and skips querying when it is denied`() =
        runTest {
            val interaction = FakeUserInteraction().apply { capabilityResult = CapabilityResult.Denied }
            val search = RecordingMediaStoreSearch()

            val result = tool(interaction, search).execute(args("report"), context())

            assertTrue(result is ToolResult.Failed)
            assertEquals(listOf(Capability.FILES), interaction.capabilityRequests)
            assertFalse(search.called)
        }

    @Test
    fun `a provider failure never echoes the provider exception text to the model`() =
        runTest {
            val secretUri = "content://secret/path"
            val search =
                object : MediaStoreSearch {
                    override fun search(
                        query: String,
                        shouldContinue: () -> Boolean,
                    ): MediaStoreQueryResult = error("failed to read $secretUri")
                }

            val result = tool(FakeUserInteraction(), search).execute(args("report"), context())

            assertTrue(result is ToolResult.Failed)
            assertFalse((result as ToolResult.Failed).reason.contains(secretUri))
        }

    @Test
    fun `queries after capability is available and returns the structured result`() =
        runTest {
            val interaction = FakeUserInteraction()
            val hit = SearchFileMetadata("report.pdf", "content://report", "Download/report.pdf", 42, 123, "application/pdf")
            val search = RecordingMediaStoreSearch(records = listOf(hit))

            val result = tool(interaction, search).execute(args("report"), context()) as ToolResult.Success
            val root = Json.parseToJsonElement(result.text).jsonObject

            assertEquals(listOf(Capability.FILES), interaction.capabilityRequests)
            assertTrue(search.called)
            assertEquals("report", search.query)
            assertEquals(
                "report.pdf",
                root
                    .getValue("items")
                    .jsonArray
                    .single()
                    .jsonObject
                    .getValue("name")
                    .jsonPrimitive.content,
            )
            assertEquals("false", root.getValue("truncated").jsonPrimitive.content)
        }

    @Test
    fun `successful search returns typed files while keeping the saved output format`() =
        runTest {
            val hit = SearchFileMetadata("report.pdf", "content://report", "Download/report.pdf", 42, 123, "application/pdf")
            val result =
                tool(FakeUserInteraction(), RecordingMediaStoreSearch(records = listOf(hit)))
                    .execute(args("report"), context()) as ToolResult.Success

            val expectedText =
                listOf(
                    """{"items":[{"name":"report.pdf","uri":"content://report","path":"Download/report.pdf","size":42,""",
                    """"dateModified":123,"mimeType":"application/pdf"}],"truncated":false,"partialAccess":false}""",
                ).joinToString("")
            assertEquals(expectedText, result.text)
            assertEquals(
                ToolContent.FilesFound(
                    listOf(ToolFile("report.pdf", "content://report", 42, "application/pdf")),
                ),
                result.content,
            )
        }

    @Test
    fun `old saved search output restores the same typed file metadata`() {
        val savedOutput =
            listOf(
                """{"items":[{"name":"Договор.pdf","uri":"content://media/external/file/1","path":""",
                """"Документы/Договор.pdf","size":1200,"dateModified":42,"mimeType":"application/pdf"}],"truncated":false}""",
            ).joinToString("")

        val content = tool(FakeUserInteraction(), RecordingMediaStoreSearch()).restoreContent(savedOutput)

        assertEquals(
            ToolContent.FilesFound(
                listOf(ToolFile("Договор.pdf", "content://media/external/file/1", 1200L, "application/pdf")),
            ),
            content,
        )
    }

    @Test
    fun `malformed legacy output and unsafe file rows restore without openable hits`() {
        val searchTool = tool(FakeUserInteraction(), RecordingMediaStoreSearch())
        val savedOutput =
            """{"items":[{"name":"private.txt","uri":"file:///private/private.txt","size":1,"mimeType":"text/plain"},""" +
                """{"name":"bad-size.txt","uri":"content://media/external/file/2","size":"2"}]}"""

        assertEquals(ToolContent.FilesFound(emptyList()), searchTool.restoreContent("not JSON"))
        assertEquals(ToolContent.FilesFound(emptyList()), searchTool.restoreContent(savedOutput))
    }

    @Test
    fun `malformed arguments fail without requesting permission or querying`() =
        runTest {
            val interaction = FakeUserInteraction()
            val search = RecordingMediaStoreSearch()

            val result = tool(interaction, search).execute(buildJsonObject { put("query", 9) }, context())

            assertTrue(result is ToolResult.Failed)
            assertEquals(emptyList<Capability>(), interaction.capabilityRequests)
            assertFalse(search.called)
        }

    @Test
    fun `stops scanning at the five second clock budget and marks results truncated`() =
        runTest {
            val clock = FakeClock()
            val interaction = FakeUserInteraction()
            val search =
                MediaStoreSearch { _, shouldContinue ->
                    val firstRowAllowed = shouldContinue()
                    clock.epochMillis += SearchFilesLimits.TIMEOUT_MILLIS
                    val secondRowAllowed = shouldContinue()
                    MediaStoreQueryResult(
                        records =
                            if (firstRowAllowed) {
                                listOf(
                                    SearchFileMetadata("first.txt", "content://first", "first.txt", 1, 1, null),
                                )
                            } else {
                                emptyList()
                            },
                        truncated = !secondRowAllowed,
                    )
                }

            val result = tool(interaction, search).execute(args("txt"), context(clock)) as ToolResult.Success
            val root = Json.parseToJsonElement(result.text).jsonObject

            assertTrue(
                root
                    .getValue("truncated")
                    .jsonPrimitive.content
                    .toBoolean(),
            )
            assertEquals(1, root.getValue("items").jsonArray.size)
        }

    @Test
    fun `declares a safe search tool and russian card`() {
        val tool = tool(FakeUserInteraction(), RecordingMediaStoreSearch())

        assertEquals("search_files", tool.spec.name)
        assertEquals(Risk.SAFE, tool.spec.risk)
        assertTrue(tool.spec.description.isNotBlank())
        val querySchema =
            tool.spec.inputSchema
                .getValue("properties")
                .jsonObject
                .getValue("query")
                .jsonObject
        assertEquals("1", querySchema.getValue("minLength").jsonPrimitive.content)
        assertEquals("200", querySchema.getValue("maxLength").jsonPrimitive.content)
        assertEquals("Поиск файлов", tool.describe(args("план")).title)
        assertTrue(tool.describe(args("план")).summary.contains("план"))
    }

    @Test
    fun `the media query runs on the dispatcher provided by the port`() =
        runTest {
            val seenThreads = mutableListOf<String>()
            val search =
                object : MediaStoreSearch {
                    override fun search(
                        query: String,
                        shouldContinue: () -> Boolean,
                    ): MediaStoreQueryResult {
                        seenThreads += Thread.currentThread().name
                        return MediaStoreQueryResult(emptyList(), truncated = false)
                    }
                }
            val io =
                Executors.newSingleThreadExecutor { r -> Thread(r, "search-test-io") }.asCoroutineDispatcher()
            try {
                val result =
                    SearchFilesTool(FakeUserInteraction(), search, FakeDispatcherProvider(main = io, io = io, default = io))
                        .execute(args("report"), context())

                assertTrue(result is ToolResult.Success)
            } finally {
                io.close()
            }
            assertTrue(
                "expected the query on the provided io dispatcher, saw $seenThreads",
                seenThreads.isNotEmpty() && seenThreads.all { it.startsWith("search-test-io") },
            )
        }

    private fun tool(
        interaction: FakeUserInteraction,
        search: MediaStoreSearch,
    ) = SearchFilesTool(interaction, search)

    private fun args(query: String) = buildJsonObject { put("query", query) }

    private fun context(clock: FakeClock = FakeClock()) = ToolContext(clock, FakeAppLogger())
}

private class RecordingMediaStoreSearch(
    private val records: List<SearchFileMetadata> = emptyList(),
) : MediaStoreSearch {
    var called: Boolean = false
    var query: String? = null

    override fun search(
        query: String,
        shouldContinue: () -> Boolean,
    ): MediaStoreQueryResult {
        called = true
        this.query = query
        return MediaStoreQueryResult(records, truncated = !shouldContinue())
    }
}
