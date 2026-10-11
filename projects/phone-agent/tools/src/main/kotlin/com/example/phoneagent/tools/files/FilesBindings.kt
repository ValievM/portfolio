package com.example.phoneagent.tools.files

import com.example.phoneagent.domain.tools.Tool
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Adds file tools to the app-wide [Tool] set (consumed by ToolRegistry) and wires their ports. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class FilesBindings {
    @Binds
    @IntoSet
    abstract fun bindFindFilesTool(tool: FindFilesTool): Tool

    @Binds
    @IntoSet
    abstract fun bindSearchFilesTool(tool: SearchFilesTool): Tool

    @Binds
    abstract fun bindMediaStoreSearch(search: AndroidMediaStoreSearch): MediaStoreSearch

    @Binds
    abstract fun bindSelectedFolder(implementation: RealSelectedFolder): SelectedFolder
}
