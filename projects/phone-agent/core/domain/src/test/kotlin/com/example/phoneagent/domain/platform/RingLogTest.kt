package com.example.phoneagent.domain.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RingLogTest {
    @Test
    fun `appends a line with a trailing newline`() {
        val result = RingLog.append(existing = "", line = "first", maxBytes = 1_000)

        assertEquals("first\n", result)
    }

    @Test
    fun `keeps appending while under the byte budget`() {
        val result = RingLog.append(existing = "first\n", line = "second", maxBytes = 1_000)

        assertEquals("first\nsecond\n", result)
    }

    @Test
    fun `drops the oldest whole lines once the byte budget is exceeded`() {
        val result = RingLog.append(existing = "aaaa\nbbbb\n", line = "cccc", maxBytes = 10)

        assertEquals("bbbb\ncccc\n", result)
        assertTrue(result.toByteArray(Charsets.UTF_8).size <= 10)
    }

    @Test
    fun `truncates a single line that alone exceeds the budget instead of failing`() {
        val result = RingLog.append(existing = "", line = "a".repeat(50), maxBytes = 10)

        assertTrue(result.toByteArray(Charsets.UTF_8).size <= 10)
        assertTrue(result.endsWith("\n"))
    }
}
