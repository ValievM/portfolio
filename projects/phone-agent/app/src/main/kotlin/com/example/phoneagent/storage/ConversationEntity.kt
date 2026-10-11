package com.example.phoneagent.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_history")
internal data class ConversationEntity(
    @PrimaryKey
    val position: Int,
    val id: String,
    val type: String,
    val text: String? = null,
    val callId: String? = null,
    val name: String? = null,
    val argumentsJson: String? = null,
    val output: String? = null,
    val provider: String? = null,
    val payloadJson: String? = null,
)
