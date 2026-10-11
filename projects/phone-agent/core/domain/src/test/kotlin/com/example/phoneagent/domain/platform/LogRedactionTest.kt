package com.example.phoneagent.domain.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRedactionTest {
    @Test
    fun `redacts bearer tokens`() {
        val redacted = LogRedaction.redact("Authorization: Bearer abc.def-123~xyz")

        assertTrue(redacted.contains("Bearer <redacted>"))
        assertFalse(redacted.contains("abc.def-123~xyz"))
    }

    @Test
    fun `redacts JWT-looking strings even without a Bearer prefix`() {
        val jwt = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U"

        val redacted = LogRedaction.redact("id_token in response: $jwt")

        assertEquals("id_token in response: <jwt>", redacted)
    }

    @Test
    fun `redacts secret values in JSON-ish text but keeps the key names and other fields`() {
        val redacted = LogRedaction.redact("""body: {"access_token":"super-secret-value","ok":true}""")

        assertFalse(redacted.contains("super-secret-value"))
        assertTrue(redacted.contains("\"access_token\":<redacted>"))
        assertTrue(redacted.contains("\"ok\":true"))
    }

    @Test
    fun `redacts secret values in query string style text`() {
        val redacted = LogRedaction.redact("code=one-time-code&code_verifier=verifier-value&state=abc")

        assertFalse(redacted.contains("one-time-code"))
        assertFalse(redacted.contains("verifier-value"))
        assertTrue(redacted.contains("code=<redacted>"))
        assertTrue(redacted.contains("code_verifier=<redacted>"))
        assertTrue(redacted.contains("state=abc"))
    }

    @Test
    fun `is case insensitive on the secret key name`() {
        val redacted = LogRedaction.redact("""{"Access_Token":"secret-value"}""")

        assertFalse(redacted.contains("secret-value"))
    }

    @Test
    fun `leaves ordinary text alone`() {
        val message = "Signing in with CHATGPT_BROWSER, http status 401"

        assertEquals(message, LogRedaction.redact(message))
    }

    @Test
    fun `sanitized throwable keeps only the original type name and the frames`() {
        val original = RuntimeException("private_contract.pdf", IllegalStateException("пользовательский текст"))

        val sanitized = LogRedaction.sanitizedThrowable(original) as SanitizedThrowable

        assertEquals(original::class.java.name, sanitized.originalType)
        assertEquals(sanitized.originalType, sanitized.message)
        assertNull(sanitized.cause)
        assertEquals(original.stackTrace.toList(), sanitized.stackTrace.toList())
    }

    @Test
    fun `an already sanitized throwable is returned as is`() {
        val first = LogRedaction.sanitizedThrowable(RuntimeException("private_contract.pdf"))

        val second = LogRedaction.sanitizedThrowable(first)

        assertTrue(first === second)
    }
}
