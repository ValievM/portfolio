package com.example.phoneagent.ui.chat

import com.example.phoneagent.domain.tools.ToolContent
import com.example.phoneagent.domain.tools.ToolOutcome
import com.example.phoneagent.ui.designsystem.components.FileHit
import com.example.phoneagent.ui.designsystem.components.ToolCallStatus

/** Maps tool-owned structured content into the UI's file-chip model. No tool protocol is parsed here. */
internal fun ToolOutcome.toFileHits(): List<FileHit> =
    ((this as? ToolOutcome.Success)?.content as? ToolContent.FilesFound)
        ?.files
        ?.map { FileHit(it.name, it.uri, it.size, it.mimeType) }
        ?.filter(FileHit::hasContentUriScheme)
        .orEmpty()

/** Chooses a card status from the outcome's type and optional structured content. */
internal fun ToolOutcome.toCardStatus(fileHitCount: Int): ToolCallStatus =
    when (this) {
        is ToolOutcome.Success -> {
            when (content) {
                is ToolContent.FilesFound -> ToolCallStatus.FilesFound(fileHitCount)
                null -> ToolCallStatus.Done(text)
            }
        }

        is ToolOutcome.Rejected -> {
            ToolCallStatus.Rejected
        }

        is ToolOutcome.Failed -> {
            ToolCallStatus.Failed(reason)
        }
    }
