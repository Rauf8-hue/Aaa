package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activity_logs")
data class ActivityLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val sender: String = "",
    val incomingMessage: String,
    val replyType: String, // "CUSTOM_REPLY", "GEMINI_AI", "ERROR"
    val replyMessage: String,
    val isSuccess: Boolean = true,
    val errorDetails: String? = null,
    val latencyMs: Long = 0
)
