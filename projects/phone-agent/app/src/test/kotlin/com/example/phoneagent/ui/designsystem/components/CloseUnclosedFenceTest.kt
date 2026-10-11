package com.example.phoneagent.ui.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Test

class CloseUnclosedFenceTest {
    @Test
    fun `text without a fence is unchanged`() {
        assertEquals("Just **text**\n\nmore", closeUnclosedFence("Just **text**\n\nmore"))
    }

    @Test
    fun `a closed fence is unchanged`() {
        val text = "Run:\n\n```kotlin\nval a = 1\n```\n\nDone"

        assertEquals(text, closeUnclosedFence(text))
    }

    @Test
    fun `an unclosed fence is closed after the last line so that line is not lost`() {
        assertEquals(
            "Run:\n\n```kotlin\nval a = 1\nval b =\n```",
            closeUnclosedFence("Run:\n\n```kotlin\nval a = 1\nval b ="),
        )
    }

    @Test
    fun `an unclosed fence with a trailing newline is closed without a blank line`() {
        assertEquals("```\ncode\n```", closeUnclosedFence("```\ncode\n"))
    }

    @Test
    fun `only an opening line so far is closed too`() {
        assertEquals("```kotlin\n```", closeUnclosedFence("```kotlin"))
    }

    @Test
    fun `a tilde fence is closed with tildes`() {
        assertEquals("~~~\ncode\n~~~", closeUnclosedFence("~~~\ncode"))
    }

    @Test
    fun `a longer opening fence needs an equally long closing fence`() {
        val text = "````md\n```\ninner\n```"

        assertEquals("$text\n````", closeUnclosedFence(text))
    }

    @Test
    fun `triple backticks in the middle of a line do not open a fence`() {
        val text = "Use ``` inline like this and go on"

        assertEquals(text, closeUnclosedFence(text))
    }

    @Test
    fun `an indented code block is not a fence`() {
        val text = "Example:\n\n        ```\n        not a fence"

        assertEquals(text, closeUnclosedFence(text))
    }

    @Test
    fun `a second fence after a closed one is handled independently`() {
        assertEquals(
            "```\na\n```\n\n```\nb\n```",
            closeUnclosedFence("```\na\n```\n\n```\nb"),
        )
    }
}
