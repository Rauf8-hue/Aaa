package com.example.data.repository

import com.example.data.local.ActivityLogDao
import com.example.model.ActivityLogEntry
import kotlinx.coroutines.flow.Flow

class ActivityLogRepository(private val dao: ActivityLogDao) {

    val allLogs: Flow<List<ActivityLogEntry>> = dao.getAllLogs()

    fun getLogsByType(type: String): Flow<List<ActivityLogEntry>> = dao.getLogsByType(type)

    suspend fun log(entry: ActivityLogEntry) {
        dao.insert(entry)
        // Cleanup old logs older than 7 days
        val cutoff = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
        dao.deleteOlderThan(cutoff)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }
}
