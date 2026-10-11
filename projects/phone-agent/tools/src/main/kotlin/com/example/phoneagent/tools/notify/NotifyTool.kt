package com.example.phoneagent.tools.notify

import com.example.phoneagent.domain.interaction.Capability
import com.example.phoneagent.domain.interaction.CapabilityResult
import com.example.phoneagent.domain.interaction.UserInteraction
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.Tool
import com.example.phoneagent.domain.tools.ToolContext
import com.example.phoneagent.domain.tools.ToolResult
import com.example.phoneagent.domain.tools.ToolSpec
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject

internal class NotifyTool
    @Inject
    constructor(
        private val userInteraction: UserInteraction,
        private val notifier: Notifier,
    ) : Tool {
        override val spec: ToolSpec =
            ToolSpec(
                name = "notify",
                description = NotifyCopy.MODEL_DESCRIPTION,
                inputSchema = notifyInputSchema,
                risk = Risk.SAFE,
                requiredCapabilities = setOf(Capability.NOTIFICATIONS),
            )

        override fun deniedMessage(capability: Capability): String = NotifyCopy.PERMISSION_DENIED

        override fun describe(args: JsonObject): CallDescription {
            val notifyArgs = decodeArgs(args)
            return CallDescription(
                title = NotifyCopy.CARD_TITLE,
                summary = notifyArgs?.let(NotifyCopy::cardSummary) ?: NotifyCopy.INVALID_ARGUMENTS,
            )
        }

        override suspend fun execute(
            args: JsonObject,
            context: ToolContext,
        ): ToolResult {
            val notifyArgs = decodeArgs(args) ?: return ToolResult.Failed(NotifyCopy.INVALID_ARGUMENTS)
            return showNotification(notifyArgs)
        }

        private suspend fun showNotification(args: NotifyArgs): ToolResult =
            when (val validation = validateNotifyArgs(args)) {
                is NotifyValidationResult.Invalid -> {
                    ToolResult.Failed(NotifyCopy.validationError(validation.error))
                }

                NotifyValidationResult.Valid -> {
                    when (userInteraction.ensure(Capability.NOTIFICATIONS)) {
                        CapabilityResult.Available -> {
                            notifier.notify(args.title, args.message)
                            ToolResult.Success(NotifyCopy.NOTIFICATION_SHOWN)
                        }

                        CapabilityResult.Denied -> {
                            ToolResult.Failed(NotifyCopy.PERMISSION_DENIED)
                        }
                    }
                }
            }
    }

private val notifyInputSchema: JsonObject =
    buildJsonObject {
        put("type", JsonPrimitive("object"))
        putJsonObject("properties") {
            putJsonObject("title") {
                put("type", JsonPrimitive("string"))
                put("minLength", JsonPrimitive(1))
                put("maxLength", JsonPrimitive(MAX_TITLE_LENGTH))
            }
            putJsonObject("message") {
                put("type", JsonPrimitive("string"))
                put("minLength", JsonPrimitive(1))
                put("maxLength", JsonPrimitive(MAX_MESSAGE_LENGTH))
            }
        }
        put(
            "required",
            buildJsonArray {
                add(JsonPrimitive("title"))
                add(JsonPrimitive("message"))
            },
        )
        put("additionalProperties", JsonPrimitive(false))
    }

private fun decodeArgs(args: JsonObject): NotifyArgs? =
    try {
        Json.decodeFromJsonElement<NotifyArgs>(args)
    } catch (_: SerializationException) {
        null
    }
