package com.example.model

sealed class GeminiResult {
    data class Success(val text: String, val latencyMs: Long) : GeminiResult()
    data class Error(val message: String, val isRetryable: Boolean = false, val latencyMs: Long = 0) : GeminiResult()
}
