package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig

class SecureApiKeyStorage(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "quantum_bot_secure_prefs"
        private const val KEY_GEMINI_API_KEY = "encrypted_gemini_api_key"
    }

    /**
     * Retrieves the configured Gemini API key.
     * Checks user saved preference first; falls back to BuildConfig if present and valid.
     */
    fun getApiKey(): String {
        val saved = prefs.getString(KEY_GEMINI_API_KEY, null)?.trim()
        if (!saved.isNullOrEmpty()) {
            return saved
        }
        // Fallback to BuildConfig if provided at build time
        val buildKey = try {
            BuildConfig.GEMINI_API_KEY.trim()
        } catch (_: Throwable) {
            ""
        }
        if (buildKey.isNotEmpty() && buildKey != "MY_GEMINI_API_KEY") {
            return buildKey
        }
        return ""
    }

    fun saveApiKey(apiKey: String) {
        prefs.edit().putString(KEY_GEMINI_API_KEY, apiKey.trim()).apply()
    }

    fun removeApiKey() {
        prefs.edit().remove(KEY_GEMINI_API_KEY).apply()
    }

    fun hasApiKey(): Boolean {
        return getApiKey().isNotEmpty()
    }

    /**
     * Returns masked key like "AIza**************9X" or "Not Configured"
     */
    fun getMaskedApiKey(): String {
        val key = getApiKey()
        if (key.isEmpty()) {
            return "Not Configured"
        }
        return if (key.length > 8) {
            val prefix = key.take(4)
            val suffix = key.takeLast(2)
            "$prefix${"*".repeat((key.length - 6).coerceIn(6, 16))}$suffix"
        } else {
            "••••••••"
        }
    }
}
