package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.CustomReply
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomReplyDao {
    @Query("SELECT * FROM custom_replies ORDER BY createdAt DESC")
    fun getAllReplies(): Flow<List<CustomReply>>

    @Query("SELECT * FROM custom_replies WHERE isEnabled = 1")
    suspend fun getEnabledReplies(): List<CustomReply>

    @Query("SELECT COUNT(*) FROM custom_replies")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reply: CustomReply): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(replies: List<CustomReply>)

    @Update
    suspend fun update(reply: CustomReply)

    @Delete
    suspend fun delete(reply: CustomReply)

    @Query("DELETE FROM custom_replies WHERE id = :id")
    suspend fun deleteById(id: Long)
}
