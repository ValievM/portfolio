package com.example.phoneagent.data.llm.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class OutputTextDeltaDto(
    val delta: String,
)

@Serializable
internal data class OutputItemDoneDto(
    val item: OutputItemDto,
)

@Serializable
internal data class OutputItemDto(
    val id: String? = null,
    val type: String,
    @SerialName("call_id") val callId: String? = null,
    val name: String? = null,
    val arguments: String? = null,
)

@Serializable
internal data class ResponseCompletedDto(
    val response: ResponseCompletedBodyDto,
)

@Serializable
internal data class ResponseCompletedBodyDto(
    val usage: UsageDto? = null,
)

@Serializable
internal data class UsageDto(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0,
)

@Serializable
internal data class ErrorEventDto(
    val error: ErrorDetailDto? = null,
)

@Serializable
internal data class ErrorDetailDto(
    val message: String? = null,
)
