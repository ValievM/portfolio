package com.example.phoneagent.tools.notify

import kotlinx.serialization.Serializable

@Serializable
public data class NotifyArgs(
    public val title: String,
    public val message: String,
)
