package com.example.whatsapp

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

class WhatsAppAccessibilityController(
    private val serviceProvider: () -> AccessibilityService?
) {

    companion object {
        private const val TAG = "WhatsAppController"
        const val WHATSAPP_PKG = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PKG = "com.whatsapp.w4b"

        private val SEND_CONTENT_DESCRIPTIONS = listOf(
            "send", "enviar", "envoyer", "invia", "kirim", "enviar mensaje", "भेजें", "भेज", "senden", "stuur", "gönder", "отправить", "إرسال"
        )
    }

    /**
     * Types and sends the given reply in the active WhatsApp conversation.
     * Retries up to [maxAttempts] times and polls for the dynamic Send button.
     */
    suspend fun sendReply(replyText: String, maxAttempts: Int = 4): Boolean {
        if (replyText.isBlank()) return false

        Log.d(TAG, "[Reply] Sending to WhatsApp: '${replyText.take(60)}...'")

        for (attempt in 1..maxAttempts) {
            val service = serviceProvider() ?: run {
                Log.w(TAG, "[Reply ERROR] AccessibilityService instance unavailable (attempt $attempt)")
                delay(300)
                return@run null
            } ?: continue

            val rootNode = service.rootInActiveWindow
            if (rootNode == null) {
                Log.w(TAG, "[Reply ERROR] rootInActiveWindow is null. WhatsApp may not be foreground active window (attempt $attempt)")
                delay(300)
                continue
            }

            val pkg = rootNode.packageName?.toString() ?: ""
            if (pkg != WHATSAPP_PKG && pkg != WHATSAPP_BUSINESS_PKG) {
                Log.w(TAG, "[Reply ERROR] Active window is '$pkg', not WhatsApp (attempt $attempt)")
                delay(300)
                continue
            }

            try {
                // 1. Locate message input field
                val inputNode = findInputField(rootNode)
                if (inputNode == null) {
                    Log.w(TAG, "[Reply ERROR] WhatsApp input field not found (attempt $attempt)")
                    delay(300)
                    continue
                }

                Log.i(TAG, "[WhatsApp] Input field found (id: ${inputNode.viewIdResourceName ?: "EditText"})")

                // 2. Set the text into input field
                val textSet = setTextIntoField(service, inputNode, replyText)
                if (!textSet) {
                    Log.w(TAG, "[Reply ERROR] Failed to set text into input field (attempt $attempt)")
                    delay(250)
                    continue
                }

                Log.i(TAG, "[WhatsApp] Text inserted")

                // 3. Poll for the dynamic Send button
                // WhatsApp converts the microphone icon to the send button after text change
                val sendButton = pollForSendButton(service, maxWaitMs = 1200)
                if (sendButton != null) {
                    val desc = sendButton.contentDescription ?: ""
                    val id = sendButton.viewIdResourceName ?: ""
                    Log.i(TAG, "[WhatsApp] Send button found (id: $id, desc: $desc)")

                    val clicked = clickNodeOrParent(sendButton)
                    if (clicked) {
                        Log.i(TAG, "[WhatsApp] Reply sent successfully")
                        return true
                    } else {
                        Log.w(TAG, "[Reply ERROR] Failed to click WhatsApp Send button (attempt $attempt)")
                    }
                } else {
                    Log.w(TAG, "[Reply ERROR] WhatsApp Send button not found after text insertion (attempt $attempt)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "[Reply ERROR] Exception during sendReply attempt $attempt: ${e.message}", e)
            }

            delay(300)
        }

        Log.e(TAG, "[Reply ERROR] Failed to send WhatsApp message after $maxAttempts attempts")
        return false
    }

    /**
     * Polls the active window for the dynamic Send button for up to [maxWaitMs] milliseconds.
     */
    private suspend fun pollForSendButton(service: AccessibilityService, maxWaitMs: Long = 1200): AccessibilityNodeInfo? {
        val intervalMs = 120L
        val maxChecks = (maxWaitMs / intervalMs).toInt().coerceAtLeast(1)

        for (check in 1..maxChecks) {
            delay(intervalMs)
            val root = service.rootInActiveWindow ?: continue
            val button = findSendButton(root)
            if (button != null) {
                return button
            }
        }
        return null
    }

    /**
     * Robust search for the WhatsApp message input field.
     * Uses ID matching first, then positional heuristic to find the chat entry at the bottom.
     */
    fun findInputField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Strategy 1: Known WhatsApp entry resource IDs
        val knownIds = listOf(
            "com.whatsapp:id/entry",
            "com.whatsapp.w4b:id/entry",
            "com.whatsapp:id/conversation_text_entry",
            "com.whatsapp:id/input_text"
        )
        for (id in knownIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                val node = nodes.firstOrNull { it.isEditable || it.className?.contains("EditText") == true }
                if (node != null) return node
            }
        }

        // Strategy 2: Search for editable fields, filtering out search bars, preferring bottom-most
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectEditableCandidates(root, candidates)

        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates[0]

        // If multiple, select candidate closest to bottom of the screen
        val rect = Rect()
        return candidates.maxByOrNull {
            it.getBoundsInScreen(rect)
            rect.bottom
        }
    }

    private fun collectEditableCandidates(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        val id = node.viewIdResourceName?.lowercase() ?: ""
        val isSearch = id.contains("search") || (node.text?.toString()?.lowercase()?.contains("search") == true)

        if (node.isEditable && !isSearch) {
            list.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectEditableCandidates(child, list)
        }
    }

    private fun setTextIntoField(
        context: Context,
        inputNode: AccessibilityNodeInfo,
        text: String
    ): Boolean {
        // Focus first
        inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        // Try ACTION_SET_TEXT
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val success = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        if (success) return true

        // Fallback: Clipboard paste
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null) {
                val clip = ClipData.newPlainText("quantum_reply", text)
                clipboard.setPrimaryClip(clip)
                inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                return inputNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[Reply ERROR] Clipboard paste fallback failed: ${e.message}")
        }

        return false
    }

    /**
     * Robust search for Send button in WhatsApp.
     * Searches by View ID, contentDescription, and clickable icon heuristics.
     */
    fun findSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Strategy 1: Known resource IDs
        val knownSendIds = listOf(
            "com.whatsapp:id/send",
            "com.whatsapp.w4b:id/send",
            "com.whatsapp:id/send_container",
            "com.whatsapp:id/send_btn",
            "com.whatsapp:id/btn_send",
            "com.whatsapp:id/conversation_send"
        )
        for (id in knownSendIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                val sendNode = nodes.firstOrNull { it.isClickable } ?: nodes.firstOrNull()
                if (sendNode != null) return sendNode
            }
        }

        // Strategy 2: Content description match
        val foundByDesc = findNodeByContentDescription(root, SEND_CONTENT_DESCRIPTIONS)
        if (foundByDesc != null) return foundByDesc

        // Strategy 3: Clickable image/button sibling containing "send"
        return findClickableSendCandidate(root)
    }

    private fun findNodeByContentDescription(
        node: AccessibilityNodeInfo,
        descriptions: List<String>
    ): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
        if (desc.isNotEmpty() && descriptions.any { desc == it || desc.contains(it) }) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByContentDescription(child, descriptions)
            if (found != null) return found
        }
        return null
    }

    private fun findClickableSendCandidate(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        if (viewId.contains("send")) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findClickableSendCandidate(child)
            if (found != null) return found
        }
        return null
    }

    /**
     * Performs a click on the given node or its parent/child if the node itself is not clickable.
     */
    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }

        // Try parent
        val parent = node.parent
        if (parent != null && parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }

        // Try child
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (child.isClickable && child.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
        }

        // Try parent's parent if needed
        val grandParent = parent?.parent
        if (grandParent != null && grandParent.isClickable && grandParent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }

        // Fallback: force action click on the node anyway
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
}

