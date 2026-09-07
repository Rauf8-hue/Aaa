package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.model.ActivityLogEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC LIMIT 200")
    fun getAllLogs(): Flow<List<ActivityLogEntry>>

    @Query("SELECT * FROM activity_logs WHERE replyType = :type ORDER BY timestamp DESC LIMIT 200")
    fun getLogsByType(type: String): Flow<List<ActivityLogEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ActivityLogEntry)

    @Query("DELETE FROM activity_logs")
    suspend fun clearAll()

    @Query("DELETE FROM activity_logs WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long)
}
