package com.example.phoneagent.tools.files

import android.content.Context
import android.net.Uri
import com.example.phoneagent.domain.storage.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/** Resolves the persisted SAF tree permission into the root node used by find_files. */
internal class RealSelectedFolder
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        @param:ApplicationContext private val context: Context,
    ) : SelectedFolder {
        override suspend fun root(): DocumentNode? =
            try {
                settingsRepository.load().searchFolderUri?.let { uri ->
                    SafDocuments.rootNode(context.contentResolver, Uri.parse(uri))
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                null
            }
    }
