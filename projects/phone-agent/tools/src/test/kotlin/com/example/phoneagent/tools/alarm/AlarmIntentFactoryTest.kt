package com.example.phoneagent.tools.alarm

import android.provider.AlarmClock
import org.junit.Assert.assertEquals
import org.junit.Test

public class AlarmIntentFactoryTest {
    @Test
    public fun `builds an alarm intent specification with all extras`() {
        val spec = AlarmIntentFactory.create(AlarmArgs(hour = 7, minute = 30, message = "Подъём"))

        assertEquals("android.intent.action.SET_ALARM", spec.action)
        assertEquals(
            mapOf(
                AlarmClock.EXTRA_HOUR to 7,
                AlarmClock.EXTRA_MINUTES to 30,
                AlarmClock.EXTRA_MESSAGE to "Подъём",
                AlarmClock.EXTRA_SKIP_UI to true,
            ),
            spec.extras,
        )
    }

    @Test
    public fun `omits the message extra when no label was supplied`() {
        val spec = AlarmIntentFactory.create(AlarmArgs(hour = 0, minute = 0))

        assertEquals(
            mapOf(
                AlarmClock.EXTRA_HOUR to 0,
                AlarmClock.EXTRA_MINUTES to 0,
                AlarmClock.EXTRA_SKIP_UI to true,
            ),
            spec.extras,
        )
    }
}
