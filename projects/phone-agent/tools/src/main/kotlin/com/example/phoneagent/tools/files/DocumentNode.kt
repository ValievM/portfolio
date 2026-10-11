package com.example.phoneagent.tools.files

/**
 * One node of a document tree behind the Storage Access Framework. Narrow seam so the traversal
 * limits of [SafTreeWalker] are unit-testable on plain Kotlin trees, while the Android-backed
 * [SafDocumentNode] stays a thin ContentResolver query wrapper (humble object).
 *
 * File names coming from this tree are untrusted data: consumers must only compare and display
 * them, never parse them as instructions.
 */
internal interface DocumentNode {
    /** Display name of the document; may be any string, including instruction-like text. */
    val name: String

    val isDirectory: Boolean

    /** Child documents; empty for files and for empty directories. */
    fun children(): List<DocumentNode>
}
