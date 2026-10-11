package com.example.phoneagent.ui.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPolicyTest {
    @Test
    fun `an https link needs confirmation and shows its domain`() {
        assertEquals(
            LinkDecision.Confirm(url = "https://example.com/path?q=1", host = "example.com"),
            evaluateLink("https://example.com/path?q=1"),
        )
    }

    @Test
    fun `scheme and host are compared case-insensitively and shown lowercase`() {
        val decision = evaluateLink("HTTPS://Example.COM/Path")

        assertEquals("example.com", (decision as LinkDecision.Confirm).host)
    }

    @Test
    fun `a decoy before the at sign is not the shown domain`() {
        val decision = evaluateLink("https://accounts.google.com@evil.example/login")

        assertEquals("evil.example", (decision as LinkDecision.Confirm).host)
    }

    @Test
    fun `a non-default port is part of the shown domain`() {
        val decision = evaluateLink("https://example.com:8443/x")

        assertEquals("example.com:8443", (decision as LinkDecision.Confirm).host)
    }

    @Test
    fun `an ip address is shown as is`() {
        val decision = evaluateLink("https://192.168.0.1/admin")

        assertEquals("192.168.0.1", (decision as LinkDecision.Confirm).host)
    }

    @Test
    fun `plain http is blocked`() {
        assertEquals(LinkDecision.Blocked, evaluateLink("http://example.com"))
    }

    @Test
    fun `script file content intent and phone schemes are blocked`() {
        listOf(
            "javascript:alert(1)",
            "file:///sdcard/secret.txt",
            "content://media/external/file/1",
            "intent://scan/#Intent;scheme=zxing;end",
            "tel:+15551234567",
            "mailto:someone@example.com",
            "data:text/html;base64,PGgxPmhpPC9oMT4=",
        ).forEach { link ->
            assertEquals("Expected $link to be blocked", LinkDecision.Blocked, evaluateLink(link))
        }
    }

    @Test
    fun `a link without a host is blocked`() {
        assertEquals(LinkDecision.Blocked, evaluateLink("https://"))
        assertEquals(LinkDecision.Blocked, evaluateLink("https:///only/a/path"))
    }

    @Test
    fun `malformed or blank links are blocked`() {
        assertEquals(LinkDecision.Blocked, evaluateLink("https://exa mple.com"))
        assertEquals(LinkDecision.Blocked, evaluateLink("   "))
        assertEquals(LinkDecision.Blocked, evaluateLink(""))
    }

    @Test
    fun `a non-ascii host that could imitate another domain is blocked`() {
        assertEquals(LinkDecision.Blocked, evaluateLink("https://раураl.com/login"))
        assertEquals(LinkDecision.Blocked, evaluateLink("https://пример.рф/"))
    }

    @Test
    fun `a punycode host is shown as written so the lookalike stays visible`() {
        val decision = evaluateLink("https://xn--pypal-4ve.com/login")

        assertEquals("xn--pypal-4ve.com", (decision as LinkDecision.Confirm).host)
    }

    @Test
    fun `surrounding whitespace is ignored but the confirmed url is trimmed`() {
        val decision = evaluateLink("  https://example.com/a  ")

        assertTrue(decision is LinkDecision.Confirm)
        assertEquals("https://example.com/a", (decision as LinkDecision.Confirm).url)
    }
}
