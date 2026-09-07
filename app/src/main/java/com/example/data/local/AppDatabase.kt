package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.model.ActivityLogEntry
import com.example.model.CustomReply
import com.example.model.ProcessedMessage

@Database(
    entities = [
        CustomReply::class,
        ActivityLogEntry::class,
        ProcessedMessage::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customReplyDao(): CustomReplyDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun processedMessageDao(): ProcessedMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "quantum_bot.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
