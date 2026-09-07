package com.example.data.repository

import com.example.data.local.CustomReplyDao
import com.example.model.CustomReply
import com.example.model.MatchMode
import kotlinx.coroutines.flow.Flow

class CustomReplyRepository(private val dao: CustomReplyDao) {

    val allReplies: Flow<List<CustomReply>> = dao.getAllReplies()

    suspend fun getEnabledReplies(): List<CustomReply> = dao.getEnabledReplies()

    suspend fun insert(reply: CustomReply): Long = dao.insert(reply)

    suspend fun update(reply: CustomReply) = dao.update(reply)

    suspend fun delete(reply: CustomReply) = dao.delete(reply)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun populateDefaultsIfEmpty() {
        if (dao.getCount() == 0) {
            val defaultReplies = listOf(
                CustomReply(
                    trigger = "Hi",
                    reply = "Hello! How can I help you?",
                    matchMode = MatchMode.CASE_INSENSITIVE.name,
                    isEnabled = true
                ),
                CustomReply(
                    trigger = "Hello",
                    reply = "Hello 👋 How can I help you?",
                    matchMode = MatchMode.CASE_INSENSITIVE.name,
                    isEnabled = true
                ),
                CustomReply(
                    trigger = "Assalamualaikum",
                    reply = "Wa Alaikum Assalam 😊 How can I help you?",
                    matchMode = MatchMode.CASE_INSENSITIVE.name,
                    isEnabled = true
                ),
                CustomReply(
                    trigger = "Pricing",
                    reply = "Thank you for asking! Our plans start with flexible options. Please let me know which service you are interested in.",
                    matchMode = MatchMode.CONTAINS.name,
                    isEnabled = true
                )
            )
            dao.insertAll(defaultReplies)
        }
    }
}
