package com.example.ai

import com.example.data.SecureApiKeyStorage
import com.example.model.BotSettings
import com.example.model.GeminiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiService(
    private val secureApiKeyStorage: SecureApiKeyStorage
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        const val DEFAULT_MODEL = "gemini-2.5-flash"
    }

    /**
     * Tests connectivity to the Gemini API using the given or stored API key.
     * Makes a small real query ("Respond with 'OK' only.") and measures latency.
     */
    suspend fun testConnection(testKey: String? = null): GeminiResult = withContext(Dispatchers.IO) {
        val apiKey = testKey?.trim()?.ifEmpty { null } ?: secureApiKeyStorage.getApiKey()
        if (apiKey.isEmpty()) {
            return@withContext GeminiResult.Error("API key is missing. Please enter your Gemini API key.")
        }

        val startTime = System.currentTimeMillis()
        val requestJson = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", "Ping test. Please reply with the single word: OK"))
                    })
                })
            }
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 10)
                put("temperature", 0.1)
            })
        }

        val url = "$BASE_URL/$DEFAULT_MODEL:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(requestJson.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val errorMessage = parseErrorMessage(response.code, responseBody)
                    return@withContext GeminiResult.Error(
                        message = errorMessage,
                        isRetryable = isCodeRetryable(response.code),
                        latencyMs = latency
                    )
                }

                val replyText = extractTextFromResponse(responseBody)
                if (replyText.isNullOrBlank()) {
                    return@withContext GeminiResult.Error(
                        message = "Gemini connected but returned an empty response.",
                        latencyMs = latency
                    )
                }

                GeminiResult.Success(replyText.trim(), latency)
            }
        } catch (e: IOException) {
            val latency = System.currentTimeMillis() - startTime
            GeminiResult.Error("Network error: Check internet connection (${e.message})", isRetryable = true, latencyMs = latency)
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            GeminiResult.Error("Unexpected error: ${e.message}", latencyMs = latency)
        }
    }

    /**
     * Generates an intelligent reply for an incoming WhatsApp message.
     * Incorporates system personality instructions, conversation context, and exponential backoff for temporary failures.
     */
    suspend fun generateReply(
        incomingMessage: String,
        settings: BotSettings,
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): GeminiResult = withContext(Dispatchers.IO) {
        val apiKey = secureApiKeyStorage.getApiKey()
        if (apiKey.isEmpty()) {
            return@withContext GeminiResult.Error("Please add your Gemini API key in Settings.")
        }

        val model = settings.geminiModel.ifBlank { DEFAULT_MODEL }
        val maxRetries = 2
        var attempt = 0
        var lastError: GeminiResult.Error? = null

        while (attempt <= maxRetries) {
            val startTime = System.currentTimeMillis()
            try {
                val requestPayload = buildRequestPayload(incomingMessage, settings, conversationHistory)
                val url = "$BASE_URL/$model:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .post(requestPayload.toString().toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val latency = System.currentTimeMillis() - startTime
                    val responseBody = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        val replyText = extractTextFromResponse(responseBody)
                        if (replyText.isNullOrBlank()) {
                            return@withContext GeminiResult.Error("Gemini returned an empty response.", latencyMs = latency)
                        }
                        return@withContext GeminiResult.Success(replyText.trim(), latency)
                    } else {
                        val errorMessage = parseErrorMessage(response.code, responseBody)
                        val retryable = isCodeRetryable(response.code)
                        lastError = GeminiResult.Error(errorMessage, retryable, latency)

                        if (!retryable) {
                            return@withContext lastError!!
                        }
                    }
                }
            } catch (e: IOException) {
                val latency = System.currentTimeMillis() - startTime
                lastError = GeminiResult.Error("No internet connection or timeout (${e.message})", isRetryable = true, latencyMs = latency)
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - startTime
                return@withContext GeminiResult.Error("Failed to parse AI response: ${e.message}", latencyMs = latency)
            }

            attempt++
            if (attempt <= maxRetries) {
                // Exponential backoff: 800ms, 1600ms
                val backoffMs = (800L * (1 shl (attempt - 1)))
                delay(backoffMs)
            }
        }

        return@withContext lastError ?: GeminiResult.Error("Gemini request failed after retries.")
    }

    private fun buildRequestPayload(
        incomingMessage: String,
        settings: BotSettings,
        conversationHistory: List<Pair<String, String>>
    ): JSONObject {
        val root = JSONObject()

        // 1. System Instruction (Personality + Language + Length + WhatsApp context)
        val systemPrompt = buildString {
            append("You are Quantum Bot, an automated WhatsApp chat assistant. ")
            append(settings.aiPersonality)
            append(" ")
            append(settings.responseLanguage.promptInstruction)
            append(" ")
            append(settings.responseLength.promptInstruction)
            append(" Rules: ")
            append("Do not include greetings like 'Dear customer' or email signatures. ")
            append("Do not wrap your answer in quotes or markdown codeblocks. ")
            append("Keep it conversational, natural, and directly suitable for an instant messaging chat.")
        }

        val systemInstructionObj = JSONObject().apply {
            put("parts", JSONArray().apply {
                put(JSONObject().put("text", systemPrompt))
            })
        }
        root.put("systemInstruction", systemInstructionObj)

        // 2. Contents (Conversation history + current message)
        val contentsArray = JSONArray()

        if (settings.conversationContextEnabled && conversationHistory.isNotEmpty()) {
            // Keep at most last 4 messages to save tokens and maintain latency
            val recentContext = conversationHistory.takeLast(4)
            for ((role, text) in recentContext) {
                val geminiRole = if (role.equals("user", ignoreCase = true)) "user" else "model"
                contentsArray.put(JSONObject().apply {
                    put("role", geminiRole)
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", text))
                    })
                })
            }
        }

        // Current incoming message
        contentsArray.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().apply {
                put(JSONObject().put("text", incomingMessage))
            })
        })
        root.put("contents", contentsArray)

        // 3. Generation Config
        root.put("generationConfig", JSONObject().apply {
            put("temperature", 0.7)
            put("maxOutputTokens", settings.responseLength.maxTokens)
        })

        return root
    }

    private fun extractTextFromResponse(jsonStr: String): String? {
        return try {
            val root = JSONObject(jsonStr)
            val candidates = root.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            if (parts.length() == 0) return null

            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("text")) {
                    sb.append(part.getString("text"))
                }
            }
            sb.toString().trim()
        } catch (_: Exception) {
            null
        }
    }

    private fun parseErrorMessage(code: Int, body: String): String {
        val serverMessage = try {
            val json = JSONObject(body)
            val error = json.optJSONObject("error")
            error?.optString("message")?.take(150)
        } catch (_: Exception) {
            null
        }

        return when (code) {
            400 -> "Invalid request or parameter (${serverMessage ?: "Bad Request"})"
            401, 403 -> "Gemini API key is invalid or unauthorized."
            404 -> "Model not found. Please verify the Gemini model name in Settings."
            429 -> "Gemini rate limit reached. Please wait a moment."
            500, 503, 504 -> "Gemini is temporarily unavailable. Retrying..."
            else -> "HTTP $code: ${serverMessage ?: "Unknown error"}"
        }
    }

    private fun isCodeRetryable(code: Int): Boolean {
        return code == 429 || code == 500 || code == 502 || code == 503 || code == 504
    }
}
