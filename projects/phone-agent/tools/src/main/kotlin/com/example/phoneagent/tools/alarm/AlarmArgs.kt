package com.example.phoneagent.tools.alarm

import kotlinx.serialization.Serializable

@Serializable
public data class AlarmArgs(
    public val hour: Int,
    public val minute: Int,
    public val message: String? = null,
)
