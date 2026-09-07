package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.model.ProcessedMessage

@Dao
interface ProcessedMessageDao {
    @Query("SELECT * FROM processed_messages WHERE messageHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): ProcessedMessage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ProcessedMessage)

    @Query("DELETE FROM processed_messages WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long)

    @Query("DELETE FROM processed_messages")
    suspend fun clearAll()
}
