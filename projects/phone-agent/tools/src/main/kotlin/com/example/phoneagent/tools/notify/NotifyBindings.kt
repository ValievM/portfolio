package com.example.phoneagent.tools.notify

import com.example.phoneagent.domain.tools.Tool
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NotifyBindings {
    @Binds
    @IntoSet
    abstract fun bindNotifyTool(tool: NotifyTool): Tool

    @Binds
    abstract fun bindNotifier(notifier: AndroidNotifier): Notifier
}
