package com.example.phoneagent.ui.chat

import com.example.phoneagent.domain.tools.ToolContent
import com.example.phoneagent.domain.tools.ToolFile
import com.example.phoneagent.domain.tools.ToolOutcome
import com.example.phoneagent.ui.designsystem.components.FileHit
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolContentMapperTest {
    @Test
    fun `structured file content maps to chips without inspecting a tool name`() {
        val outcome =
            ToolOutcome.Success(
                "model-facing output",
                ToolContent.FilesFound(
                    listOf(ToolFile("report.pdf", "content://media/external/downloads/42", 1024, "application/pdf")),
                ),
            )

        assertEquals(
            listOf(FileHit("report.pdf", "content://media/external/downloads/42", 1024L, "application/pdf")),
            outcome.toFileHits(),
        )
    }

    @Test
    fun `output text that looks like file json is never parsed by the ui`() {
        val jsonText =
            """{"items":[{"name":"report.pdf","uri":"content://media/external/downloads/42","size":1024,""" +
                """"mimeType":"application/pdf"}]}"""
        val outcome =
            ToolOutcome.Success(
                jsonText,
            )

        assertEquals(emptyList<FileHit>(), outcome.toFileHits())
    }

    @Test
    fun `structured files with a non content uri are not offered to open`() {
        val outcome =
            ToolOutcome.Success(
                "model-facing output",
                ToolContent.FilesFound(listOf(ToolFile("private.txt", "file:///private/private.txt", 1, "text/plain"))),
            )

        assertEquals(emptyList<FileHit>(), outcome.toFileHits())
    }
}
