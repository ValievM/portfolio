package com.example.phoneagent.tools.alarm

import com.example.phoneagent.domain.tools.Tool
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AlarmBindings {
    @Binds
    @IntoSet
    abstract fun bindAlarmTool(tool: AlarmTool): Tool

    @Binds
    abstract fun bindAlarmIntentPort(launcher: AlarmIntentLauncher): AlarmIntentPort
}
