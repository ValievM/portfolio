package com.example.phoneagent.di

import com.example.phoneagent.domain.agent.AgentConfig
import com.example.phoneagent.domain.agent.AgentLoop
import com.example.phoneagent.domain.conversation.ContextWindowPolicy
import com.example.phoneagent.domain.interaction.UserInteraction
import com.example.phoneagent.domain.model.ModelGateway
import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.Clock
import com.example.phoneagent.domain.platform.DefaultDispatcherProvider
import com.example.phoneagent.domain.platform.DispatcherProvider
import com.example.phoneagent.domain.platform.IdGenerator
import com.example.phoneagent.domain.platform.RandomIdGenerator
import com.example.phoneagent.domain.tools.Tool
import com.example.phoneagent.domain.tools.ToolPipeline
import com.example.phoneagent.domain.tools.ToolRegistry
import com.example.phoneagent.interaction.UserInteractionBridge
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AgentBindingsModule {
    @Binds
    @Singleton
    abstract fun bindUserInteraction(implementation: UserInteractionBridge): UserInteraction
}

@Module
@InstallIn(SingletonComponent::class)
internal object AgentModule {
    @Provides
    fun provideIdGenerator(): IdGenerator = RandomIdGenerator

    @Provides
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider

    // Every Tool registered anywhere (Hilt @IntoSet, see AlarmBindings/FilesBindings/NotifyBindings)
    // is aggregated into this one Set<Tool> automatically; ToolRegistry never knows the concrete types.
    @Provides
    @Singleton
    fun provideToolRegistry(tools: Set<@JvmSuppressWildcards Tool>): ToolRegistry = ToolRegistry(tools)

    @Provides
    @Singleton
    fun provideToolPipeline(
        registry: ToolRegistry,
        userInteraction: UserInteraction,
        clock: Clock,
        logger: AppLogger,
    ): ToolPipeline = ToolPipeline(registry, userInteraction, clock, logger)

    @Provides
    fun provideAgentLoop(
        modelGateway: ModelGateway,
        toolPipeline: ToolPipeline,
        contextWindowPolicy: ContextWindowPolicy,
        idGenerator: IdGenerator,
    ): AgentLoop = AgentLoop(modelGateway, toolPipeline, contextWindowPolicy, idGenerator, AgentConfig())
}
