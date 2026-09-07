package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_replies")
data class CustomReply(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val trigger: String,
    val reply: String,
    val matchMode: String = MatchMode.CASE_INSENSITIVE.name,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
