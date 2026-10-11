package com.example.phoneagent.tools.alarm

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.warn
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

private const val TAG = "AlarmIntentLauncher"

internal interface AlarmIntentPort {
    fun launch(spec: AlarmIntentSpec): AlarmLaunchResult
}

internal class AlarmIntentLauncher
    @Inject
    constructor(
        @param:ApplicationContext private val applicationContext: Context,
        private val logger: AppLogger,
    ) : AlarmIntentPort {
        override fun launch(spec: AlarmIntentSpec): AlarmLaunchResult {
            val intent = spec.toAndroidIntent()
            if (intent.resolveActivity(applicationContext.packageManager) == null) {
                return AlarmLaunchResult.NoClockApp
            }

            return try {
                applicationContext.startActivity(intent)
                AlarmLaunchResult.Started
            } catch (_: ActivityNotFoundException) {
                AlarmLaunchResult.NoClockApp
            } catch (exception: SecurityException) {
                // Caught, not thrown: the tool must report a normal Failed outcome, not crash the
                // turn — but the reason is easy to lose (e.g. background-activity-launch
                // restrictions), so it goes to the log rather than being silently discarded.
                logger.warn(TAG, "startActivity(SET_ALARM) rejected", throwable = exception)
                AlarmLaunchResult.Failed
            }
        }
    }

internal sealed interface AlarmLaunchResult {
    data object Started : AlarmLaunchResult

    data object NoClockApp : AlarmLaunchResult

    data object Failed : AlarmLaunchResult
}

private fun AlarmIntentSpec.toAndroidIntent(): Intent =
    Intent(action)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .also { intent ->
            extras.forEach { (key, value) ->
                when (value) {
                    is Boolean -> intent.putExtra(key, value)
                    is Int -> intent.putExtra(key, value)
                    is String -> intent.putExtra(key, value)
                }
            }
        }
