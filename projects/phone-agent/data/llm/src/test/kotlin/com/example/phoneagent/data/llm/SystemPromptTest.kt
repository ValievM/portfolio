package com.example.phoneagent.data.llm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPromptTest {
    @Test
    fun `is not blank`() {
        assertTrue(SYSTEM_PROMPT.isNotBlank())
    }

    @Test
    fun `warns that tool and file output is untrusted data`() {
        assertTrue(SYSTEM_PROMPT.contains("недовер", ignoreCase = true))
    }

    @Test
    fun `forbids following instructions found inside tool or file content`() {
        assertTrue(SYSTEM_PROMPT.contains("не выполняй", ignoreCase = true))
    }

    @Test
    fun `restricts the model to the tools listed on the request`() {
        assertTrue(SYSTEM_PROMPT.contains("только инструменты", ignoreCase = true))
    }

    @Test
    fun `clarifies that file tools return metadata, not file content`() {
        assertTrue(SYSTEM_PROMPT.contains("метаданн", ignoreCase = true))
    }

    @Test
    fun `does not hardcode a tool name so new tools need no prompt change`() {
        assertFalse(SYSTEM_PROMPT.contains("set_alarm"))
        assertFalse(SYSTEM_PROMPT.contains("find_files"))
        assertFalse(SYSTEM_PROMPT.contains("search_files"))
        assertFalse(SYSTEM_PROMPT.contains("notify"))
    }

    @Test
    fun `is used as the default backend profile instructions`() {
        assertTrue(BackendProfile.ChatGptSubscription.instructions == SYSTEM_PROMPT)
    }
}
