package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.model.BotSettings
import com.example.model.ResponseLanguage
import com.example.model.ResponseLength
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BotSettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<BotSettings> = _settings.asStateFlow()

    private fun loadSettings(): BotSettings {
        return BotSettings(
            isBotEnabled = prefs.getBoolean(KEY_BOT_ENABLED, false),
            isAutoReplyEnabled = prefs.getBoolean(KEY_AUTO_REPLY_ENABLED, true),
            replyPrivateChats = prefs.getBoolean(KEY_REPLY_PRIVATE_CHATS, true),
            replyGroupChats = prefs.getBoolean(KEY_REPLY_GROUP_CHATS, false),
            replyDelaySeconds = prefs.getInt(KEY_REPLY_DELAY, 1),
            aiPersonality = prefs.getString(
                KEY_AI_PERSONALITY,
                "You are a helpful and polite customer assistant. Keep replies friendly, natural, and helpful."
            ) ?: "",
            responseLanguage = try {
                ResponseLanguage.valueOf(prefs.getString(KEY_RESPONSE_LANG, ResponseLanguage.AUTO.name) ?: ResponseLanguage.AUTO.name)
            } catch (_: Exception) {
                ResponseLanguage.AUTO
            },
            responseLength = try {
                ResponseLength.valueOf(prefs.getString(KEY_RESPONSE_LENGTH, ResponseLength.SHORT.name) ?: ResponseLength.SHORT.name)
            } catch (_: Exception) {
                ResponseLength.SHORT
            },
            conversationContextEnabled = prefs.getBoolean(KEY_CONVERSATION_CONTEXT, true),
            isLoggingEnabled = prefs.getBoolean(KEY_LOGGING_ENABLED, true),
            geminiModel = prefs.getString(KEY_GEMINI_MODEL, "gemini-2.5-flash") ?: "gemini-2.5-flash"
        )
    }

    fun updateSettings(newSettings: BotSettings) {
        prefs.edit()
            .putBoolean(KEY_BOT_ENABLED, newSettings.isBotEnabled)
            .putBoolean(KEY_AUTO_REPLY_ENABLED, newSettings.isAutoReplyEnabled)
            .putBoolean(KEY_REPLY_PRIVATE_CHATS, newSettings.replyPrivateChats)
            .putBoolean(KEY_REPLY_GROUP_CHATS, newSettings.replyGroupChats)
            .putInt(KEY_REPLY_DELAY, newSettings.replyDelaySeconds)
            .putString(KEY_AI_PERSONALITY, newSettings.aiPersonality)
            .putString(KEY_RESPONSE_LANG, newSettings.responseLanguage.name)
            .putString(KEY_RESPONSE_LENGTH, newSettings.responseLength.name)
            .putBoolean(KEY_CONVERSATION_CONTEXT, newSettings.conversationContextEnabled)
            .putBoolean(KEY_LOGGING_ENABLED, newSettings.isLoggingEnabled)
            .putString(KEY_GEMINI_MODEL, newSettings.geminiModel)
            .apply()

        _settings.value = newSettings
    }

    fun setBotEnabled(enabled: Boolean) {
        updateSettings(_settings.value.copy(isBotEnabled = enabled))
    }

    fun setAutoReplyEnabled(enabled: Boolean) {
        updateSettings(_settings.value.copy(isAutoReplyEnabled = enabled))
    }

    fun setReplyPrivateChats(enabled: Boolean) {
        updateSettings(_settings.value.copy(replyPrivateChats = enabled))
    }

    fun setReplyGroupChats(enabled: Boolean) {
        updateSettings(_settings.value.copy(replyGroupChats = enabled))
    }

    fun setReplyDelaySeconds(seconds: Int) {
        updateSettings(_settings.value.copy(replyDelaySeconds = seconds))
    }

    companion object {
        private const val PREFS_NAME = "quantum_bot_settings"
        private const val KEY_BOT_ENABLED = "is_bot_enabled"
        private const val KEY_AUTO_REPLY_ENABLED = "is_auto_reply_enabled"
        private const val KEY_REPLY_PRIVATE_CHATS = "reply_private_chats"
        private const val KEY_REPLY_GROUP_CHATS = "reply_group_chats"
        private const val KEY_REPLY_DELAY = "reply_delay_seconds"
        private const val KEY_AI_PERSONALITY = "ai_personality"
        private const val KEY_RESPONSE_LANG = "response_language"
        private const val KEY_RESPONSE_LENGTH = "response_length"
        private const val KEY_CONVERSATION_CONTEXT = "conversation_context"
        private const val KEY_LOGGING_ENABLED = "logging_enabled"
        private const val KEY_GEMINI_MODEL = "gemini_model"
    }
}
