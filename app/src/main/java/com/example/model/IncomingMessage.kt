package com.example.model

data class IncomingMessage(
    val text: String,
    val sender: String = "",
    val isGroup: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
