package com.example.phoneagent.di

import android.content.Context
import androidx.room.Room
import com.example.phoneagent.domain.storage.ConversationRepository
import com.example.phoneagent.storage.AppDatabase
import com.example.phoneagent.storage.ConversationDao
import com.example.phoneagent.storage.RoomConversationRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ConversationRepositoryBindingsModule {
    @Binds
    @Singleton
    abstract fun bindConversationRepository(implementation: RoomConversationRepository): ConversationRepository
}

@Module
@InstallIn(SingletonComponent::class)
internal object ConversationDatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "phone-agent.db").build()

    @Provides
    fun provideConversationDao(database: AppDatabase): ConversationDao = database.conversationDao()
}
