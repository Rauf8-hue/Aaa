package com.example.model

enum class MatchMode(val label: String, val description: String) {
    CASE_INSENSITIVE("Case Insensitive", "Matches text ignoring UPPER/lowercase and basic punctuation"),
    EXACT("Exact Match", "Matches text strictly with exact case and symbols"),
    CONTAINS("Contains", "Triggers if the incoming message contains the trigger word")
}

enum class ResponseLanguage(val label: String, val promptInstruction: String) {
    AUTO("Auto Detect", "Detect the user's language and respond naturally in the exact same language."),
    ENGLISH("English", "Always respond in clean, natural English."),
    HINDI("Hindi", "Always respond in Hindi (Devanagari or Romanized according to user's style)."),
    HINGLISH("Hinglish", "Respond in friendly colloquial Hinglish (Hindi written in Latin script)."),
    URDU("Urdu", "Always respond in Urdu.")
}

enum class ResponseLength(val label: String, val maxTokens: Int, val promptInstruction: String) {
    SHORT("Short (1-2 sentences)", 80, "Keep your response very concise, maximum 1 to 2 short sentences."),
    MEDIUM("Medium (Paragraph)", 180, "Respond with a standard conversational paragraph, helpful and clear."),
    LONG("Long (Detailed)", 350, "Provide a comprehensive, detailed reply covering all relevant points.")
}

data class BotSettings(
    val isBotEnabled: Boolean = false,
    val isAutoReplyEnabled: Boolean = true,
    val replyPrivateChats: Boolean = true,
    val replyGroupChats: Boolean = false,
    val replyDelaySeconds: Int = 1,
    val aiPersonality: String = "You are a helpful and polite customer assistant. Keep replies friendly, natural, and helpful.",
    val responseLanguage: ResponseLanguage = ResponseLanguage.AUTO,
    val responseLength: ResponseLength = ResponseLength.SHORT,
    val conversationContextEnabled: Boolean = true,
    val isLoggingEnabled: Boolean = true,
    val geminiModel: String = "gemini-2.5-flash"
)
