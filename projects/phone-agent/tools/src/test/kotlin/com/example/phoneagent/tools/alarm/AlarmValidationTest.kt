package com.example.phoneagent.tools.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

public class AlarmValidationTest {
    @Test
    public fun `accepts boundary alarm times`() {
        assertEquals(AlarmValidationResult.Valid, validateAlarmArgs(AlarmArgs(hour = 0, minute = 0)))
        assertEquals(AlarmValidationResult.Valid, validateAlarmArgs(AlarmArgs(hour = 23, minute = 59)))
    }

    @Test
    public fun `rejects an hour above 23`() {
        assertEquals(
            AlarmValidationResult.Invalid("Час должен быть от 0 до 23"),
            validateAlarmArgs(AlarmArgs(hour = 25, minute = 0)),
        )
    }

    @Test
    public fun `rejects a negative hour`() {
        assertEquals(
            AlarmValidationResult.Invalid("Час должен быть от 0 до 23"),
            validateAlarmArgs(AlarmArgs(hour = -1, minute = 0)),
        )
    }

    @Test
    public fun `rejects a minute above 59`() {
        assertEquals(
            AlarmValidationResult.Invalid("Минуты должны быть от 0 до 59"),
            validateAlarmArgs(AlarmArgs(hour = 0, minute = 60)),
        )
    }

    @Test
    public fun `rejects a negative minute`() {
        assertEquals(
            AlarmValidationResult.Invalid("Минуты должны быть от 0 до 59"),
            validateAlarmArgs(AlarmArgs(hour = 0, minute = -1)),
        )
    }
}
