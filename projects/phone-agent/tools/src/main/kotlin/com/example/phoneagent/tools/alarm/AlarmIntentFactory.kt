package com.example.phoneagent.tools.alarm

public object AlarmIntentFactory {
    public fun create(args: AlarmArgs): AlarmIntentSpec {
        val extras =
            buildMap {
                put(EXTRA_HOUR, args.hour)
                put(EXTRA_MINUTES, args.minute)
                args.message?.let { put(EXTRA_MESSAGE, it) }
                put(EXTRA_SKIP_UI, true)
            }
        return AlarmIntentSpec(action = ACTION_SET_ALARM, extras = extras)
    }
}

internal const val ACTION_SET_ALARM: String = "android.intent.action.SET_ALARM"
internal const val EXTRA_HOUR: String = "android.intent.extra.alarm.HOUR"
internal const val EXTRA_MINUTES: String = "android.intent.extra.alarm.MINUTES"
internal const val EXTRA_MESSAGE: String = "android.intent.extra.alarm.MESSAGE"
internal const val EXTRA_SKIP_UI: String = "android.intent.extra.alarm.SKIP_UI"
