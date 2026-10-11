package com.example.phoneagent.tools.alarm

import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.Tool
import com.example.phoneagent.domain.tools.ToolContext
import com.example.phoneagent.domain.tools.ToolResult
import com.example.phoneagent.domain.tools.ToolSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private val LAUNCH_SPACING: Duration = 1_000.milliseconds

internal class AlarmTool
    internal constructor(
        private val intentLauncher: AlarmIntentPort,
        private val spacing: Duration,
        private val timeSource: TimeSource,
    ) : Tool {
        @Inject
        constructor(intentLauncher: AlarmIntentPort) : this(intentLauncher, LAUNCH_SPACING, TimeSource.Monotonic)

        override val spec: ToolSpec =
            ToolSpec(
                name = "set_alarm",
                description = "Set an alarm on the device.",
                inputSchema = alarmInputSchema,
                risk = Risk.CONFIRM,
            )

        override fun describe(args: JsonObject): CallDescription {
            val alarmArgs =
                decodeArgs(args)
                    ?: return CallDescription(title = AlarmCopy.CARD_TITLE, summary = AlarmCopy.CARD_SUMMARY_INVALID_ARGS)
            return CallDescription(
                title = AlarmCopy.CARD_TITLE,
                summary = AlarmCopy.cardSummary(alarmArgs.hour, alarmArgs.minute, alarmArgs.message),
            )
        }

        override suspend fun execute(
            args: JsonObject,
            context: ToolContext,
        ): ToolResult = decodeArgs(args)?.let { executeAlarm(it) } ?: ToolResult.Failed(AlarmCopy.RESULT_INVALID_ARGS)

        private val launchLock = Mutex()
        private var lastLaunch: TimeMark? = null

        private suspend fun executeAlarm(alarmArgs: AlarmArgs): ToolResult =
            when (val validation = validateAlarmArgs(alarmArgs)) {
                is AlarmValidationResult.Invalid -> ToolResult.Failed(validation.reason)
                AlarmValidationResult.Valid -> launchSpaced(alarmArgs)
            }

        /**
         * The clock app takes an alarm request as an activity launch and reports nothing back. Several
         * launches within milliseconds (a confirmed batch) make it keep only the first, while this tool
         * still reports success for all of them. So consecutive launches are at least [spacing] apart.
         */
        private suspend fun launchSpaced(alarmArgs: AlarmArgs): ToolResult =
            launchLock.withLock {
                lastLaunch?.let { previous ->
                    val remaining = spacing - previous.elapsedNow()
                    if (remaining.isPositive()) delay(remaining)
                }
                val result = intentLauncher.launch(AlarmIntentFactory.create(alarmArgs))
                lastLaunch = timeSource.markNow()
                when (result) {
                    AlarmLaunchResult.Started -> ToolResult.Success(AlarmCopy.RESULT_SUCCESS)
                    AlarmLaunchResult.NoClockApp -> ToolResult.Failed(AlarmCopy.RESULT_NO_CLOCK_APP)
                    AlarmLaunchResult.Failed -> ToolResult.Failed(AlarmCopy.RESULT_FAILED)
                }
            }
    }

private val alarmInputSchema: JsonObject =
    buildJsonObject {
        put("type", JsonPrimitive("object"))
        putJsonObject("properties") {
            putJsonObject("hour") {
                put("type", JsonPrimitive("integer"))
                put("minimum", JsonPrimitive(0))
                put("maximum", JsonPrimitive(23))
            }
            putJsonObject("minute") {
                put("type", JsonPrimitive("integer"))
                put("minimum", JsonPrimitive(0))
                put("maximum", JsonPrimitive(59))
            }
            putJsonObject("message") {
                put("type", JsonPrimitive("string"))
            }
        }
        put(
            "required",
            buildJsonArray {
                add(JsonPrimitive("hour"))
                add(JsonPrimitive("minute"))
            },
        )
        put("additionalProperties", JsonPrimitive(false))
    }

private fun decodeArgs(args: JsonObject): AlarmArgs? =
    try {
        Json.decodeFromJsonElement<AlarmArgs>(args)
    } catch (_: SerializationException) {
        null
    }
