package com.example.phoneagent.tools.notify

import org.junit.Assert.assertEquals
import org.junit.Test

public class NotifyValidationTest {
    @Test
    public fun `accepts title and message at their maximum lengths`() {
        val args = NotifyArgs(title = "t".repeat(MAX_TITLE_LENGTH), message = "m".repeat(MAX_MESSAGE_LENGTH))

        assertEquals(NotifyValidationResult.Valid, validateNotifyArgs(args))
    }

    @Test
    public fun `rejects a blank title`() {
        assertEquals(
            NotifyValidationResult.Invalid(NotifyValidationError.EMPTY_TITLE),
            validateNotifyArgs(NotifyArgs(title = "  ", message = "Сообщение")),
        )
    }

    @Test
    public fun `rejects a blank message`() {
        assertEquals(
            NotifyValidationResult.Invalid(NotifyValidationError.EMPTY_MESSAGE),
            validateNotifyArgs(NotifyArgs(title = "Заголовок", message = "\t")),
        )
    }

    @Test
    public fun `rejects a title longer than the maximum`() {
        assertEquals(
            NotifyValidationResult.Invalid(NotifyValidationError.TITLE_TOO_LONG),
            validateNotifyArgs(NotifyArgs(title = "t".repeat(MAX_TITLE_LENGTH + 1), message = "Сообщение")),
        )
    }

    @Test
    public fun `rejects a message longer than the maximum`() {
        assertEquals(
            NotifyValidationResult.Invalid(NotifyValidationError.MESSAGE_TOO_LONG),
            validateNotifyArgs(NotifyArgs(title = "Заголовок", message = "m".repeat(MAX_MESSAGE_LENGTH + 1))),
        )
    }
}
