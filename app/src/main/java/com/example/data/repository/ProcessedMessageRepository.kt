package com.example.data.repository

import com.example.data.local.ProcessedMessageDao
import com.example.model.ProcessedMessage
import java.security.MessageDigest

class ProcessedMessageRepository(private val dao: ProcessedMessageDao) {

    /**
     * Checks if this message has already been processed within the recent deduplication window.
     */
    suspend fun isMessageProcessed(sender: String, messageText: String): Boolean {
        val hash = computeHash(sender, messageText)
        val existing = dao.findByHash(hash)
        if (existing != null) {
            // Check if processed within last 30 minutes
            val isRecent = (System.currentTimeMillis() - existing.timestamp) < (30 * 60 * 1000)
            return isRecent
        }
        return false
    }

    suspend fun markProcessed(sender: String, messageText: String) {
        val hash = computeHash(sender, messageText)
        dao.insert(
            ProcessedMessage(
                messageHash = hash,
                messageText = messageText,
                sender = sender,
                timestamp = System.currentTimeMillis()
            )
        )
        // Clean up messages older than 24 hours
        val cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        dao.deleteOlderThan(cutoff)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    private fun computeHash(sender: String, text: String): String {
        val normalized = "${sender.trim().lowercase()}:${text.trim().lowercase()}"
        val bytes = MessageDigest.getInstance("MD5").digest(normalized.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
