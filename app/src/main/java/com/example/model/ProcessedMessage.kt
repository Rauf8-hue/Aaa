package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "processed_messages")
data class ProcessedMessage(
    @PrimaryKey
    val messageHash: String,
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sender: String = ""
)
