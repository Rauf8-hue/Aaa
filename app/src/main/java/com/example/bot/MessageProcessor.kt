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
    // In-memory conversation context cache per sender (e.g. sender -> list of (role, message))
    private val chatContextMap = ConcurrentHashMap<String, MutableList<Pair<String, String>>>()

    /**
     * Primary entry point when a new WhatsApp message is detected.
     */
    fun onNewWhatsAppMessage(message: IncomingMessage) {
        scope.launch(Dispatchers.IO) {
            processMutex.withLock {
                processMessageInternal(message)
            }
        }
    }

    private suspend fun processMessageInternal(message: IncomingMessage) {
        val trimmedText = message.text.trim()
        if (trimmedText.isBlank()) {
            return
        }

        val settings = settingsRepository.settings.value

        // 1. Check if Bot is enabled
        if (!settings.isBotEnabled) {
            Log.d(TAG, "Bot is disabled, ignoring incoming message")
            return
        }

        // 2. Check if Auto Reply is enabled
        if (!settings.isAutoReplyEnabled) {
            Log.d(TAG, "Auto Reply is disabled, ignoring incoming message")
            return
        }

        // 3. Check Chat Type Settings
        if (message.isGroup && !settings.replyGroupChats) {
            Log.d(TAG, "Group chat replies are disabled, ignoring group message from ${message.sender}")
            return
        }
        if (!message.isGroup && !settings.replyPrivateChats) {
            Log.d(TAG, "Private chat replies are disabled, ignoring private message from ${message.sender}")
            return
        }

        // 4. Duplicate Check
        if (processedMessageRepository.isMessageProcessed(message.sender, trimmedText)) {
            Log.d(TAG, "Message already processed recently, ignoring: '$trimmedText'")
            return
        }

        val senderKey = if (message.sender.isNotBlank()) message.sender else "default_chat"

        // 5. Check Custom Auto Reply Rules (Checked BEFORE Gemini)
        val customRules = customReplyRepository.getEnabledReplies()
        val matchedRule = customReplyEngine.findMatchingReply(trimmedText, customRules)

        if (matchedRule != null) {
            Log.i(TAG, "Custom rule matched: '${matchedRule.trigger}' -> '${matchedRule.reply}'")
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

        // 6. Gemini AI Fallback
        Log.i(TAG, "No custom rule matched. Calling Gemini AI fallback for '$trimmedText'")

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
                    Log.w(TAG, "Gemini returned empty or invalid text, skipping sending")
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
                Log.e(TAG, "Gemini generation failed: ${geminiResult.message}")
                // Do NOT send an error message to the customer
                // Log failure locally for user visibility
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
            delay(delaySeconds * 1000L)
        }

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
                // Trim oldest context
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
            Log.e(TAG, "Failed to send WhatsApp message via Accessibility")
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
}
