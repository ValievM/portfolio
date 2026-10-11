package com.example.phoneagent.storage

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ConversationEntity::class], version = 1, exportSchema = true)
internal abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
}
