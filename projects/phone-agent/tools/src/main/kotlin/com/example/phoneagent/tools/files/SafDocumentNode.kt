package com.example.phoneagent.tools.files

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract

/**
 * [DocumentNode] backed by the Storage Access Framework: children come from a
 * ContentResolver.query against the document's child-documents URI. Thin Android wrapper by
 * design — no logic lives here (matching and limits are [SafTreeWalker]); per
 * docs/architecture/testing.md this wrapper is verified manually on a device, not by units.
 */
internal class SafDocumentNode(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
    private val documentId: String,
    override val name: String,
    override val isDirectory: Boolean,
) : DocumentNode {
    override fun children(): List<DocumentNode> {
        if (isDirectory) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            val cursor = resolver.query(childrenUri, CHILD_PROJECTION, null, null, null)
            if (cursor != null) {
                return cursor.use { it.readChildren(resolver, treeUri) }
            }
        }
        return emptyList()
    }

    private companion object {
        // Column order for every projection here: document id, display name, mime type.
        private val CHILD_PROJECTION = documentProjection()
    }
}

internal object SafDocuments {
    /**
     * Node for the root of a persisted folder tree Uri (from OpenDocumentTree +
     * takePersistableUriPermission), or null when the provider can no longer serve it (e.g. the
     * permission was revoked); callers turn that into a tool failure, not a crash.
     */
    fun rootNode(
        resolver: ContentResolver,
        treeUri: Uri,
    ): SafDocumentNode? {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        val cursor = resolver.query(documentUri, ROOT_PROJECTION, null, null, null)
        return cursor?.use { it.readRoot(resolver, treeUri, rootId) }
    }

    private val ROOT_PROJECTION = documentProjection()
}

private fun documentProjection(): Array<String> =
    arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
    )

private fun Cursor.readChildren(
    resolver: ContentResolver,
    treeUri: Uri,
): List<DocumentNode> =
    buildList {
        while (moveToNext()) {
            val childId = getString(0)
            val childName = getString(1)
            if (childId != null && childName != null) {
                add(
                    SafDocumentNode(
                        resolver = resolver,
                        treeUri = treeUri,
                        documentId = childId,
                        name = childName,
                        isDirectory = getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                    ),
                )
            }
        }
    }

private fun Cursor.readRoot(
    resolver: ContentResolver,
    treeUri: Uri,
    rootId: String,
): SafDocumentNode? =
    if (moveToFirst()) {
        SafDocumentNode(
            resolver = resolver,
            treeUri = treeUri,
            documentId = rootId,
            name = getString(1) ?: "",
            isDirectory = getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
        )
    } else {
        null
    }
