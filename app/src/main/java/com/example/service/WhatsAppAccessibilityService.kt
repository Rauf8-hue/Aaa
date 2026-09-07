package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.QuantumBotApp
import com.example.model.IncomingMessage
import com.example.whatsapp.WhatsAppAccessibilityController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WhatsAppAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "WhatsAppAccService"

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected = _isServiceConnected.asStateFlow()

        @Volatile
        var instance: WhatsAppAccessibilityService? = null
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastProcessedText: String = ""
    private var lastProcessedTimestamp: Long = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceConnected.value = true
        Log.i(TAG, "WhatsAppAccessibilityService connected successfully")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkgName = event.packageName?.toString() ?: return
        if (pkgName != WhatsAppAccessibilityController.WHATSAPP_PKG &&
            pkgName != WhatsAppAccessibilityController.WHATSAPP_BUSINESS_PKG
        ) {
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                handleWhatsAppContentChanged()
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "WhatsAppAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceConnected.value = false
        serviceScope.cancel()
        Log.i(TAG, "WhatsAppAccessibilityService destroyed")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        _isServiceConnected.value = false
        return super.onUnbind(intent)
    }

    private fun handleWhatsAppContentChanged() {
        val rootNode = rootInActiveWindow ?: return

        serviceScope.launch {
            try {
                val incoming = extractLatestIncomingMessage(rootNode)
                if (incoming != null && incoming.text.isNotBlank()) {
                    val now = System.currentTimeMillis()
                    // Debounce rapid identical events within 1.5 seconds
                    if (incoming.text == lastProcessedText && (now - lastProcessedTimestamp) < 1500) {
                        return@launch
                    }

                    lastProcessedText = incoming.text
                    lastProcessedTimestamp = now

                    Log.d(TAG, "[WhatsApp] Message detected: '${incoming.text}' from '${incoming.sender}' (isGroup=${incoming.isGroup})")
                    Log.d(TAG, "[Message] Text extracted: '${incoming.text}'")

                    QuantumBotApp.instance.messageProcessor.onNewWhatsAppMessage(incoming)
                }
            } catch (e: Exception) {
                Log.e(TAG, "[WhatsApp ERROR] Error parsing WhatsApp conversation: ${e.message}", e)
            }
        }
    }

    /**
     * Traverses the conversation hierarchy to extract the latest incoming message.
     */
    private fun extractLatestIncomingMessage(root: AccessibilityNodeInfo): IncomingMessage? {
        // 1. Extract Chat Title / Sender name from conversation header
        val chatTitle = extractChatTitle(root)
        val isGroup = isGroupChat(root, chatTitle)

        // 2. Find conversation messages list
        val messageNodes = mutableListOf<AccessibilityNodeInfo>()
        collectMessageNodes(root, messageNodes)

        if (messageNodes.isEmpty()) {
            return null
        }

        // 3. Inspect message nodes from bottom up (newest message first)
        for (i in messageNodes.indices.reversed()) {
            val node = messageNodes[i]
            val text = node.text?.toString()?.trim() ?: continue
            if (text.isEmpty()) continue

            // Filter out timestamps, system info, audio durations, or user's own status
            if (isIgnorableSystemText(text)) continue

            // Determine if message is outgoing (sent by user or bot)
            if (isOutgoingMessage(node)) {
                // If the latest message in the chat is outgoing, we should not reply to it
                return null
            }

            // Valid incoming message found
            return IncomingMessage(
                text = text,
                sender = chatTitle,
                isGroup = isGroup
            )
        }

        return null
    }

    private fun extractChatTitle(root: AccessibilityNodeInfo): String {
        val titleIds = listOf(
            "com.whatsapp:id/conversation_contact_name",
            "com.whatsapp.w4b:id/conversation_contact_name",
            "com.whatsapp:id/title",
            "com.whatsapp:id/conversation_title"
        )
        for (id in titleIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                val title = nodes.firstOrNull()?.text?.toString()?.trim()
                if (!title.isNullOrEmpty()) return title
            }
        }

        // Fallback: search top action bar children
        return "WhatsApp Contact"
    }

    private fun isGroupChat(root: AccessibilityNodeInfo, title: String): Boolean {
        val groupStatusIds = listOf(
            "com.whatsapp:id/conversation_contact_status",
            "com.whatsapp:id/group_indicator",
            "com.whatsapp:id/chat_subtitle"
        )
        for (id in groupStatusIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                val statusText = nodes.firstOrNull()?.text?.toString()?.lowercase() ?: ""
                if (statusText.contains("tap here for group info") || statusText.contains("participants") || statusText.contains(",")) {
                    return true
                }
            }
        }
        return false
    }

    private fun collectMessageNodes(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        // Strictly exclude editable inputs (e.g. WhatsApp chat entry field, search bar)
        if (node.isEditable || node.className?.contains("EditText", ignoreCase = true) == true) {
            return
        }

        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        // Exclude system views, headers, search bars, and entry containers
        if (viewId.contains("entry") ||
            viewId.contains("search") ||
            viewId.contains("contact_name") ||
            viewId.contains("title") ||
            viewId.contains("status") ||
            viewId.contains("toolbar") ||
            viewId.contains("action_bar")
        ) {
            return
        }

        val isMessageText = viewId.contains("message_text") ||
                viewId.contains("caption") ||
                (node.className?.contains("TextView", ignoreCase = true) == true && !node.text.isNullOrBlank())

        if (isMessageText && !node.text.isNullOrBlank()) {
            list.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectMessageNodes(child, list)
        }
    }

    private fun isOutgoingMessage(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 5) {
            val resName = current.viewIdResourceName?.lowercase() ?: ""
            if (resName.contains("outgoing") ||
                resName.contains("message_out") ||
                resName.contains("msg_out") ||
                resName.contains("row_chat_out") ||
                resName.contains("bubble_out")
            ) {
                return true
            }

            val desc = current.contentDescription?.toString()?.lowercase() ?: ""
            if (desc.contains("read") ||
                desc.contains("delivered") ||
                desc.contains("sent") ||
                desc.contains("pending") ||
                desc.contains("seen")
            ) {
                return true
            }

            current = current.parent
            depth++
        }
        return false
    }

    private fun isIgnorableSystemText(text: String): Boolean {
        val t = text.lowercase().trim()
        if (t.matches("^\\d{1,2}:\\d{2}(\\s?(am|pm))?$".toRegex())) return true // Time only
        if (t == "online" || t == "typing..." || t == "last seen" || t.startsWith("last seen")) return true
        if (t.contains("messages and calls are end-to-end encrypted")) return true
        if (t == "today" || t == "yesterday") return true
        return false
    }
}

