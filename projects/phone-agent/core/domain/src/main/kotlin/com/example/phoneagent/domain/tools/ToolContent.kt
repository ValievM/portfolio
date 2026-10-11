package com.example.phoneagent.domain.tools

/** Optional typed result data returned alongside the text sent to the model; it is not persisted. */
public sealed interface ToolContent {
    public data class FilesFound(
        public val files: List<ToolFile>,
    ) : ToolContent
}

/** File metadata that a client can render or use for an explicitly confirmed open action. */
public data class ToolFile(
    public val name: String,
    public val uri: String,
    public val size: Long?,
    public val mimeType: String?,
)
