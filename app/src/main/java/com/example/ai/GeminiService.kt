package com.example.ai

import android.util.Log
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
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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
        private const val TAG = "GeminiService"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        const val DEFAULT_MODEL = "gemini-2.5-flash"
    }

    /**
     * Tests connectivity and authentication to the Gemini API.
     * Performs a real request with a ping prompt and returns latency and response details.
     * Uses the exact same underlying architecture as the WhatsApp auto-reply pipeline.
     */
    suspend fun testConnection(testKey: String? = null, model: String? = null): GeminiResult = withContext(Dispatchers.IO) {
        val apiKey = testKey?.trim()?.ifEmpty { null } ?: secureApiKeyStorage.getApiKey()
        val maskedKey = secureApiKeyStorage.getMaskedApiKey()

        if (apiKey.isEmpty()) {
            Log.e(TAG, "[Gemini ERROR] API key is missing. Please configure your Gemini API key.")
            return@withContext GeminiResult.Error("API key is missing. Please configure your Gemini API key in Settings.")
        }

        val targetModel = model?.ifBlank { null } ?: DEFAULT_MODEL
        Log.d(TAG, "[Gemini] Request started (testConnection, model=$targetModel)")
        Log.d(TAG, "[Gemini] API key loaded (${if (testKey != null) "provided test key" else maskedKey})")

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

        val url = "$BASE_URL/$targetModel:generateContent?key=$apiKey"
        Log.d(TAG, "[Gemini] Request sent")

        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .post(requestJson.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                val responseBody = response.body?.string() ?: ""

                Log.d(TAG, "[Gemini] HTTP status: ${response.code}")

                if (!response.isSuccessful) {
                    val errorMessage = parseErrorMessage(response.code, responseBody, targetModel)
                    Log.e(TAG, "[Gemini ERROR] HTTP ${response.code}: $errorMessage")
                    return@withContext GeminiResult.Error(
                        message = errorMessage,
                        isRetryable = isCodeRetryable(response.code),
                        latencyMs = latency
                    )
                }

                Log.d(TAG, "[Gemini] Response received (${latency}ms)")
                val replyText = extractTextFromResponse(responseBody)
                if (replyText.isNullOrBlank()) {
                    Log.e(TAG, "[Gemini ERROR] Empty response received from Gemini")
                    return@withContext GeminiResult.Error(
                        message = "Gemini connected successfully but returned an empty response.",
                        latencyMs = latency
                    )
                }

                Log.d(TAG, "[Gemini] Text extracted: '$replyText'")
                GeminiResult.Success(replyText.trim(), latency)
            }
        } catch (e: SocketTimeoutException) {
            val latency = System.currentTimeMillis() - startTime
            Log.e(TAG, "[Gemini ERROR] Request timed out (${e.message})")
            GeminiResult.Error("Request timed out: Gemini did not respond within 45 seconds.", isRetryable = true, latencyMs = latency)
        } catch (e: UnknownHostException) {
            val latency = System.currentTimeMillis() - startTime
            Log.e(TAG, "[Gemini ERROR] Network unavailable - host not found (${e.message})")
            GeminiResult.Error("No internet connection. Unable to reach Gemini API servers.", isRetryable = true, latencyMs = latency)
        } catch (e: ConnectException) {
            val latency = System.currentTimeMillis() - startTime
            Log.e(TAG, "[Gemini ERROR] Connection failed (${e.message})")
            GeminiResult.Error("Network connection failed. Check your internet connection.", isRetryable = true, latencyMs = latency)
        } catch (e: IOException) {
            val latency = System.currentTimeMillis() - startTime
            Log.e(TAG, "[Gemini ERROR] Network IO error (${e.message})")
            GeminiResult.Error("Network error: ${e.message}", isRetryable = true, latencyMs = latency)
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            Log.e(TAG, "[Gemini ERROR] Unexpected error (${e.message})", e)
            GeminiResult.Error("Unexpected error: ${e.message}", latencyMs = latency)
        }
    }

    /**
     * Generates an intelligent auto-reply for an incoming WhatsApp message.
     * Incorporates system personality instructions, conversation context, and exponential backoff.
     */
    suspend fun generateReply(
        incomingMessage: String,
        settings: BotSettings,
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): GeminiResult = withContext(Dispatchers.IO) {
        val apiKey = secureApiKeyStorage.getApiKey()
        val maskedKey = secureApiKeyStorage.getMaskedApiKey()

        if (apiKey.isEmpty()) {
            Log.e(TAG, "[Gemini ERROR] API key is missing. Configure in Settings.")
            return@withContext GeminiResult.Error("Gemini API key is missing. Please enter your API key in Settings.")
        }

        val model = settings.geminiModel.ifBlank { DEFAULT_MODEL }
        Log.d(TAG, "[Gemini] Request started")
        Log.d(TAG, "[Gemini] API key loaded ($maskedKey)")

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
                    .addHeader("x-goog-api-key", apiKey)
                    .post(requestPayload.toString().toRequestBody(jsonMediaType))
                    .build()

                Log.d(TAG, "[Gemini] Request sent (attempt ${attempt + 1})")

                httpClient.newCall(request).execute().use { response ->
                    val latency = System.currentTimeMillis() - startTime
                    val responseBody = response.body?.string() ?: ""

                    Log.d(TAG, "[Gemini] HTTP status: ${response.code}")

                    if (response.isSuccessful) {
                        Log.d(TAG, "[Gemini] Response received (${latency}ms)")
                        val replyText = extractTextFromResponse(responseBody)
                        if (replyText.isNullOrBlank()) {
                            Log.e(TAG, "[Gemini ERROR] Empty text extracted from response")
                            return@withContext GeminiResult.Error("Gemini returned an empty response.", latencyMs = latency)
                        }

                        val cleanReply = cleanGeneratedReply(replyText)
                        Log.d(TAG, "[Gemini] Text extracted: '${cleanReply.take(60)}...'")
                        return@withContext GeminiResult.Success(cleanReply, latency)
                    } else {
                        val errorMessage = parseErrorMessage(response.code, responseBody, model)
                        Log.e(TAG, "[Gemini ERROR] HTTP ${response.code}: $errorMessage")
                        val retryable = isCodeRetryable(response.code)
                        lastError = GeminiResult.Error(errorMessage, retryable, latency)

                        if (!retryable) {
                            return@withContext lastError!!
                        }
                    }
                }
            } catch (e: SocketTimeoutException) {
                val latency = System.currentTimeMillis() - startTime
                Log.e(TAG, "[Gemini ERROR] Request timed out on attempt ${attempt + 1}: ${e.message}")
                lastError = GeminiResult.Error("Request timed out: Gemini did not respond.", isRetryable = true, latencyMs = latency)
            } catch (e: UnknownHostException) {
                val latency = System.currentTimeMillis() - startTime
                Log.e(TAG, "[Gemini ERROR] Unknown host (no internet): ${e.message}")
                lastError = GeminiResult.Error("No internet connection.", isRetryable = true, latencyMs = latency)
            } catch (e: IOException) {
                val latency = System.currentTimeMillis() - startTime
                Log.e(TAG, "[Gemini ERROR] IO error on attempt ${attempt + 1}: ${e.message}")
                lastError = GeminiResult.Error("Network error: ${e.message}", isRetryable = true, latencyMs = latency)
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - startTime
                Log.e(TAG, "[Gemini ERROR] Unexpected error on attempt ${attempt + 1}: ${e.message}", e)
                return@withContext GeminiResult.Error("Failed to parse AI response: ${e.message}", latencyMs = latency)
            }

            attempt++
            if (attempt <= maxRetries) {
                // Exponential backoff: 800ms, 1600ms
                val backoffMs = (800L * (1 shl (attempt - 1)))
                Log.d(TAG, "[Gemini] Temporary error, retrying in ${backoffMs}ms (attempt $attempt of $maxRetries)...")
                delay(backoffMs)
            }
        }

        Log.e(TAG, "[Gemini ERROR] API request failed after all retries: ${lastError?.message}")
        return@withContext lastError ?: GeminiResult.Error("Gemini request failed after retries.")
    }

    private fun buildRequestPayload(
        incomingMessage: String,
        settings: BotSettings,
        conversationHistory: List<Pair<String, String>>
    ): JSONObject {
        val root = JSONObject()

        // 1. System Instruction (Personality + Language + Length + WhatsApp guidelines)
        val systemPrompt = buildString {
            append("You are Quantum Bot, an automated WhatsApp chat assistant. ")
            append(settings.aiPersonality)
            append(" ")
            append(settings.responseLanguage.promptInstruction)
            append(" ")
            append(settings.responseLength.promptInstruction)
            append(" Guidelines: ")
            append("Never include greetings like 'Dear customer' or email signatures. ")
            append("Do not wrap your answer in quotes or markdown codeblocks. ")
            append("Keep it natural, direct, and suitable for an instant WhatsApp message.")
        }

        val systemInstructionObj = JSONObject().apply {
            put("parts", JSONArray().apply {
                put(JSONObject().put("text", systemPrompt))
            })
        }
        root.put("systemInstruction", systemInstructionObj)

        // 2. Contents (Sanitized Conversation history + current incoming message)
        val contentsArray = JSONArray()

        if (settings.conversationContextEnabled && conversationHistory.isNotEmpty()) {
            val validHistory = sanitizeConversationHistory(conversationHistory)
            for ((role, text) in validHistory) {
                contentsArray.put(JSONObject().apply {
                    put("role", role)
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", text))
                    })
                })
            }
        }

        // Current incoming message (always role: "user")
        contentsArray.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().apply {
                put(JSONObject().put("text", incomingMessage.trim()))
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

    /**
     * Sanitizes conversation history so that Gemini API constraints are strictly satisfied:
     * - Only alternating turns: user -> model -> user -> model.
     * - First item must have role "user".
     * - Last item before incomingMessage must have role "model" (so adding user message preserves alternation).
     * - No empty or duplicate turns.
     */
    private fun sanitizeConversationHistory(history: List<Pair<String, String>>): List<Pair<String, String>> {
        val cleanList = mutableListOf<Pair<String, String>>()
        for ((rawRole, text) in history) {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) continue

            val standardRole = if (rawRole.equals("user", ignoreCase = true)) "user" else "model"
            if (cleanList.isEmpty()) {
                // First element in Gemini contents must be from "user"
                if (standardRole == "user") {
                    cleanList.add(standardRole to trimmed)
                }
            } else {
                val lastRole = cleanList.last().first
                if (standardRole != lastRole) {
                    cleanList.add(standardRole to trimmed)
                }
            }
        }

        // Limit to at most the last 4 turns
        var trimmedHistory = cleanList.takeLast(4)
        // Ensure trimmed history still starts with "user"
        while (trimmedHistory.isNotEmpty() && trimmedHistory.first().first != "user") {
            trimmedHistory = trimmedHistory.drop(1)
        }
        // Ensure trimmed history ends with "model" so that adding the new incoming "user" message alternates correctly
        while (trimmedHistory.isNotEmpty() && trimmedHistory.last().first != "model") {
            trimmedHistory = trimmedHistory.dropLast(1)
        }

        return trimmedHistory
    }

    private fun extractTextFromResponse(jsonStr: String): String? {
        return try {
            val root = JSONObject(jsonStr)

            // Check for safety filter block in promptFeedback
            val promptFeedback = root.optJSONObject("promptFeedback")
            val blockReason = promptFeedback?.optString("blockReason")
            if (!blockReason.isNullOrEmpty()) {
                Log.w(TAG, "[Gemini ERROR] Prompt was blocked by safety filter: $blockReason")
                return null
            }

            val candidates = root.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null

            val firstCandidate = candidates.getJSONObject(0)

            // Check finishReason
            val finishReason = firstCandidate.optString("finishReason")
            if (finishReason == "SAFETY" || finishReason == "RECITATION" || finishReason == "BLOCKLIST") {
                Log.w(TAG, "[Gemini ERROR] Response was blocked by Gemini filter (finishReason: $finishReason)")
                return null
            }

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
        } catch (e: Exception) {
            Log.e(TAG, "[Gemini ERROR] Failed to parse JSON response: ${e.message}", e)
            null
        }
    }

    private fun cleanGeneratedReply(rawText: String): String {
        var clean = rawText.trim()
        // Strip wrapping quotes if present
        if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length > 1) {
            clean = clean.substring(1, clean.length - 1).trim()
        }
        // Strip markdown backticks if present
        if (clean.startsWith("```") && clean.endsWith("```") && clean.length > 6) {
            clean = clean.removeSurrounding("```").trim()
            if (clean.startsWith("markdown", ignoreCase = true) || clean.startsWith("text", ignoreCase = true)) {
                clean = clean.substringAfter("\n").trim()
            }
        }
        return clean
    }

    private fun parseErrorMessage(code: Int, body: String, model: String): String {
        val serverMessage = try {
            val json = JSONObject(body)
            val error = json.optJSONObject("error")
            error?.optString("message")?.take(200)
        } catch (_: Exception) {
            null
        }

        return when {
            code == 400 && serverMessage?.contains("API key", ignoreCase = true) == true ->
                "Gemini API key is invalid or expired. Please update it in Settings."
            code == 400 ->
                "Bad Request (HTTP 400): ${serverMessage ?: "Invalid parameters sent to Gemini API"}"
            code == 401 || code == 403 ->
                "Gemini API key is unauthorized or invalid (HTTP $code). Please check your key in Settings."
            code == 404 ->
                "Model '$model' not found (HTTP 404). Please verify model name in Settings."
            code == 429 ->
                "Gemini rate limit exceeded (HTTP 429). Please wait a moment."
            code in 500..599 ->
                "Gemini service temporarily unavailable (HTTP $code). Retrying..."
            else ->
                "HTTP $code: ${serverMessage ?: "Unknown error from Gemini"}"
        }
    }

    private fun isCodeRetryable(code: Int): Boolean {
        return code == 429 || code in 500..504
    }
}

