package com.example.whatsapp

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
            "send", "enviar", "envoyer", "invia", "kirim", "enviar mensaje", "भेजें", "senden", "stuur"
        )
    }

    /**
     * Attempts to type and send the given reply in the active WhatsApp conversation.
     * Uses retries to handle keyboard popping up or delayed UI rendering.
     */
    suspend fun sendReply(replyText: String, maxAttempts: Int = 4): Boolean {
        if (replyText.isBlank()) return false

        for (attempt in 1..maxAttempts) {
            val service = serviceProvider() ?: run {
                Log.w(TAG, "AccessibilityService instance unavailable (attempt $attempt)")
                delay(300)
                return@run null
            } ?: continue

            val rootNode = service.rootInActiveWindow
            if (rootNode == null) {
                Log.w(TAG, "rootInActiveWindow is null (attempt $attempt)")
                delay(300)
                continue
            }

            try {
                // 1. Locate message input field
                val inputNode = findInputField(rootNode)
                if (inputNode == null) {
                    Log.w(TAG, "Message input field not found on attempt $attempt")
                    delay(350)
                    continue
                }

                // 2. Set the text
                val textSet = setTextIntoField(service, inputNode, replyText)
                if (!textSet) {
                    Log.w(TAG, "Failed to set text into input field on attempt $attempt")
                    delay(300)
                    continue
                }

                // Give WhatsApp UI brief moment to transition microphone icon to send icon
                delay(250)

                // 3. Locate and click Send button
                val currentRoot = service.rootInActiveWindow ?: rootNode
                val sendButton = findSendButton(currentRoot)
                if (sendButton != null) {
                    val clicked = sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) {
                        Log.i(TAG, "Successfully sent WhatsApp reply: $replyText")
                        return true
                    }
                } else {
                    Log.w(TAG, "Send button not found on attempt $attempt after text insertion")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during sendReply attempt $attempt: ${e.message}", e)
            }

            delay(300)
        }

        return false
    }

    /**
     * Robust search for the WhatsApp message input field.
     * Uses ID matching first, then EditText heuristic traversal.
     */
    fun findInputField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Strategy 1: Known resource IDs
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

        // Strategy 2: Recursive traversal looking for editable EditText
        return findEditableNode(root)
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val className = node.className?.toString() ?: ""
        if (node.isEditable && (className.contains("EditText", ignoreCase = true) || className.contains("TextView", ignoreCase = true))) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableNode(child)
            if (found != null) return found
        }
        return null
    }

    private fun setTextIntoField(
        context: Context,
        inputNode: AccessibilityNodeInfo,
        text: String
    ): Boolean {
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
            Log.e(TAG, "Clipboard paste fallback failed: ${e.message}")
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
            "com.whatsapp:id/send_container"
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

        // Strategy 3: Clickable image/button sibling
        return findClickableSendCandidate(root)
    }

    private fun findNodeByContentDescription(
        node: AccessibilityNodeInfo,
        descriptions: List<String>
    ): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
        if (desc.isNotEmpty() && descriptions.any { desc.contains(it) } && node.isClickable) {
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
        if (viewId.contains("send") && node.isClickable) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findClickableSendCandidate(child)
            if (found != null) return found
        }
        return null
    }
}
