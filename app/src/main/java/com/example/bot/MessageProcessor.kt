package com.example.bot

import android.util.Log
import com.example.ai.GeminiService
import com.example.data.repository.ActivityLogRepository
import com.example.data.repository.BotSettingsRepository
import com.example.data.repository.CustomReplyRepository
import com.example.data.repository.ProcessedMessageRepository
import com.example.model.ActivityLogEntry
import com.example.model.GeminiResult
import com.example.model.IncomingMessage
import com.example.whatsapp.WhatsAppAccessibilityController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class MessageProcessor(
    private val settingsRepository: BotSettingsRepository,
    private val customReplyRepository: CustomReplyRepository,
    private val processedMessageRepository: ProcessedMessageRepository,
    private val activityLogRepository: ActivityLogRepository,
    private val customReplyEngine: CustomReplyEngine,
    private val geminiService: GeminiService,
    private val accessibilityController: WhatsAppAccessibilityController,
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "MessageProcessor"
    }

    private val processMutex = Mutex()
    // In-memory conversation context cache per sender (sender -> list of (role, message))
    private val chatContextMap = ConcurrentHashMap<String, MutableList<Pair<String, String>>>()
    // Prevent duplicate in-flight processing of the exact same message while Gemini is generating
    private val inFlightKeys = ConcurrentHashMap.newKeySet<String>()
    // Prevent bot self-reply loops: store recently sent replies with timestamp (expires after 30s)
    private val recentSentReplies = ConcurrentHashMap<String, Long>()

    /**
     * Primary entry point when a new WhatsApp message is detected.
     */
    fun onNewWhatsAppMessage(message: IncomingMessage) {
        val trimmedText = message.text.trim()
        if (trimmedText.isBlank()) return

        val senderKey = if (message.sender.isNotBlank()) message.sender else "default_chat"
        val messageKey = "$senderKey::$trimmedText"

        // Discard if already being processed in-flight
        if (!inFlightKeys.add(messageKey)) {
            Log.d(TAG, "[Bot] Message is already in-flight, skipping duplicate event: '$trimmedText'")
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                processMutex.withLock {
                    processMessageInternal(message, senderKey)
                }
            } finally {
                inFlightKeys.remove(messageKey)
            }
        }
    }

    private suspend fun processMessageInternal(message: IncomingMessage, senderKey: String) {
        val trimmedText = message.text.trim()
        val settings = settingsRepository.settings.value

        // 1. Check if Bot is enabled
        if (!settings.isBotEnabled) {
            Log.d(TAG, "[Bot] Bot is disabled in settings, ignoring message")
            return
        }

        // 2. Check if Auto Reply is enabled
        if (!settings.isAutoReplyEnabled) {
            Log.d(TAG, "[Bot] Auto Reply is disabled in settings, ignoring message")
            return
        }

        // 3. Check Chat Type Settings (Group vs Private)
        if (message.isGroup && !settings.replyGroupChats) {
            Log.d(TAG, "[Bot] Group chat replies are disabled, ignoring group message from ${message.sender}")
            return
        }
        if (!message.isGroup && !settings.replyPrivateChats) {
            Log.d(TAG, "[Bot] Private chat replies are disabled, ignoring private message from ${message.sender}")
            return
        }

        // 4. Bot Self-Reply Loop Prevention: Check if this message is our own recently sent reply
        cleanOldSentReplies()
        if (recentSentReplies.containsKey(trimmedText)) {
            Log.d(TAG, "[Bot] Message matches recently sent reply from Quantum Bot, ignoring loop")
            return
        }

        // 5. Duplicate Check against persistent storage
        if (processedMessageRepository.isMessageProcessed(message.sender, trimmedText)) {
            Log.d(TAG, "[Bot] Message already processed recently in database, ignoring: '$trimmedText'")
            return
        }

        Log.i(TAG, "[Bot] Message accepted from '${message.sender}'")

        // 6. Check Custom Auto Reply Rules (Checked BEFORE Gemini)
        Log.d(TAG, "[CustomReply] Checking rules...")
        val customRules = customReplyRepository.getEnabledReplies()
        val matchedRule = customReplyEngine.findMatchingReply(trimmedText, customRules)

        if (matchedRule != null) {
            Log.i(TAG, "[CustomReply] Matched rule: '${matchedRule.trigger}' -> '${matchedRule.reply}'")
            executeReply(
                incoming = message,
                replyText = matchedRule.reply,
                replyType = "CUSTOM_REPLY",
                senderKey = senderKey,
                delaySeconds = settings.replyDelaySeconds,
                latencyMs = 0
            )
            return
        }

        Log.d(TAG, "[CustomReply] No matching rule")

        // 7. Gemini AI Fallback
        Log.i(TAG, "[Gemini] Request started for message: '${trimmedText.take(50)}'")

        val history = if (settings.conversationContextEnabled) {
            chatContextMap[senderKey]?.toList() ?: emptyList()
        } else {
            emptyList()
        }

        val geminiResult = geminiService.generateReply(
            incomingMessage = trimmedText,
            settings = settings,
            conversationHistory = history
        )

        when (geminiResult) {
            is GeminiResult.Success -> {
                val cleanAiReply = geminiResult.text.trim()
                if (cleanAiReply.isBlank() || cleanAiReply.equals("null", ignoreCase = true)) {
                    Log.w(TAG, "[Gemini ERROR] Gemini returned empty or null text, skipping sending")
                    if (settings.isLoggingEnabled) {
                        activityLogRepository.log(
                            ActivityLogEntry(
                                sender = message.sender,
                                incomingMessage = trimmedText,
                                replyType = "ERROR",
                                replyMessage = "",
                                isSuccess = false,
                                errorDetails = "Gemini returned an empty reply",
                                latencyMs = geminiResult.latencyMs
                            )
                        )
                    }
                    return
                }

                Log.d(TAG, "[Gemini] Text extracted: '${cleanAiReply.take(60)}'")

                executeReply(
                    incoming = message,
                    replyText = cleanAiReply,
                    replyType = "GEMINI_AI",
                    senderKey = senderKey,
                    delaySeconds = settings.replyDelaySeconds,
                    latencyMs = geminiResult.latencyMs
                )
            }
            is GeminiResult.Error -> {
                Log.e(TAG, "[Gemini ERROR] API request failed: ${geminiResult.message}")
                // Do NOT send an error message or apology to the WhatsApp customer
                // Log failure locally for user visibility in the activity tab
                if (settings.isLoggingEnabled) {
                    activityLogRepository.log(
                        ActivityLogEntry(
                            sender = message.sender,
                            incomingMessage = trimmedText,
                            replyType = "ERROR",
                            replyMessage = "",
                            isSuccess = false,
                            errorDetails = geminiResult.message,
                            latencyMs = geminiResult.latencyMs
                        )
                    )
                }
            }
        }
    }

    private suspend fun executeReply(
        incoming: IncomingMessage,
        replyText: String,
        replyType: String,
        senderKey: String,
        delaySeconds: Int,
        latencyMs: Long
    ) {
        // Apply configurable reply delay
        if (delaySeconds > 0) {
            Log.d(TAG, "[Bot] Waiting configurable delay of ${delaySeconds}s before sending...")
            delay(delaySeconds * 1000L)
        }

        // Record in loop prevention cache before sending
        recentSentReplies[replyText.trim()] = System.currentTimeMillis()

        // Send via WhatsAppAccessibilityController
        val sendSuccess = accessibilityController.sendReply(replyText)

        if (sendSuccess) {
            // Mark as processed in database cache
            processedMessageRepository.markProcessed(incoming.sender, incoming.text)

            // Update conversation context
            val historyList = chatContextMap.getOrPut(senderKey) { mutableListOf() }
            historyList.add("user" to incoming.text)
            historyList.add("model" to replyText)
            if (historyList.size > 8) {
                while (historyList.size > 8) {
                    historyList.removeAt(0)
                }
            }

            // Log activity
            val settings = settingsRepository.settings.value
            if (settings.isLoggingEnabled) {
                activityLogRepository.log(
                    ActivityLogEntry(
                        sender = incoming.sender,
                        incomingMessage = incoming.text,
                        replyType = replyType,
                        replyMessage = replyText,
                        isSuccess = true,
                        latencyMs = latencyMs
                    )
                )
            }
        } else {
            Log.e(TAG, "[Reply ERROR] Failed to send WhatsApp message via Accessibility")
            val settings = settingsRepository.settings.value
            if (settings.isLoggingEnabled) {
                activityLogRepository.log(
                    ActivityLogEntry(
                        sender = incoming.sender,
                        incomingMessage = incoming.text,
                        replyType = "ERROR",
                        replyMessage = replyText,
                        isSuccess = false,
                        errorDetails = "WhatsApp reply field or Send button was not accessible",
                        latencyMs = latencyMs
                    )
                )
            }
        }
    }

    private fun cleanOldSentReplies() {
        val now = System.currentTimeMillis()
        val expiredThreshold = 30_000L // 30 seconds
        val iterator = recentSentReplies.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > expiredThreshold) {
                iterator.remove()
            }
        }
    }
}

