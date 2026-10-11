package com.example.phoneagent.tools.files

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.BaseColumns
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal data class MediaStoreQueryResult(
    val records: List<SearchFileMetadata>,
    val truncated: Boolean,
    /** True when at least one collection was blocked (e.g. partially granted media access). */
    val inaccessible: Boolean = false,
)

internal fun interface MediaStoreSearch {
    fun search(
        query: String,
        shouldContinue: () -> Boolean,
    ): MediaStoreQueryResult
}

/** Thin ContentResolver adapter; it reads only MediaStore metadata and never opens file contents. */
internal class AndroidMediaStoreSearch
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : MediaStoreSearch {
        private val resolver: ContentResolver = context.contentResolver

        override fun search(
            query: String,
            shouldContinue: () -> Boolean,
        ): MediaStoreQueryResult {
            val records = mutableListOf<SearchFileMetadata>()
            var truncated = false
            var inaccessible = false
            collections().forEach { collection ->
                if (shouldContinue()) {
                    val scan = scanCollection(collection, query, shouldContinue)
                    records += scan.records
                    truncated = truncated || scan.truncated
                    inaccessible = inaccessible || scan.inaccessible
                } else {
                    truncated = true
                }
            }
            return MediaStoreQueryResult(records, truncated, inaccessible)
        }

        private fun scanCollection(
            collection: CollectionKind,
            query: String,
            shouldContinue: () -> Boolean,
        ): MediaStoreQueryResult =
            try {
                queryCollection(collection.uri, query)?.use { rows -> scanRows(rows, collection, shouldContinue) }
                    ?: MediaStoreQueryResult(emptyList(), truncated = false)
            } catch (_: SecurityException) {
                // Android may deny individual MediaStore collections for the granted scope;
                // that is missing access, not an incomplete scan.
                MediaStoreQueryResult(emptyList(), truncated = false, inaccessible = true)
            }

        private fun scanRows(
            rows: Cursor,
            collection: CollectionKind,
            shouldContinue: () -> Boolean,
        ): MediaStoreQueryResult {
            val columns = ColumnIndexes(rows)
            val records = mutableListOf<SearchFileMetadata>()
            var complete = true
            while (rows.moveToNext()) {
                if (!shouldContinue()) {
                    complete = false
                    break
                }
                rows.toMetadata(collection, columns)?.let(records::add)
            }
            return MediaStoreQueryResult(records, truncated = !complete)
        }

        private fun collections(): List<CollectionKind> =
            buildList {
                add(CollectionKind.FILES)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add(CollectionKind.DOWNLOADS)
                }
            }

        private fun queryCollection(
            uri: Uri,
            query: String,
        ): Cursor? =
            resolver.query(
                uri,
                projection(),
                "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? ESCAPE '\\'",
                arrayOf("%${query.escapeLikeWildcards()}%"),
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )

        private fun projection(): Array<String> =
            buildList {
                add(BaseColumns._ID)
                add(MediaStore.MediaColumns.DISPLAY_NAME)
                add(MediaStore.MediaColumns.SIZE)
                add(MediaStore.MediaColumns.DATE_MODIFIED)
                add(MediaStore.MediaColumns.MIME_TYPE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.MediaColumns.RELATIVE_PATH)
            }.toTypedArray()

        private fun Cursor.toMetadata(
            collection: CollectionKind,
            columns: ColumnIndexes,
        ): SearchFileMetadata? {
            val id = getLong(columns.id)
            val name = getString(columns.name) ?: return null
            val relativePath = columns.relativePath?.let { getString(it) }.orEmpty()
            val fullPath = if (relativePath.isEmpty()) name else "$relativePath$name"
            val contentUri = ContentUris.withAppendedId(collection.uri, id).toString()
            val size = columns.size?.let { if (isNull(it)) null else getLong(it) } ?: 0L
            val date = columns.dateModified?.let { if (isNull(it)) null else getLong(it) }
            val mime = columns.mimeType?.let { if (isNull(it)) null else getString(it) }
            return SearchFileMetadata(name, contentUri, fullPath, size, date, mime)
        }

        private data class ColumnIndexes(
            val id: Int,
            val name: Int,
            val size: Int?,
            val dateModified: Int?,
            val mimeType: Int?,
            val relativePath: Int?,
        ) {
            constructor(cursor: Cursor) :
                this(
                    id = cursor.getColumnIndexOrThrow(BaseColumns._ID),
                    name = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME),
                    size = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE).takeIf { it >= 0 },
                    dateModified = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED).takeIf { it >= 0 },
                    mimeType = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE).takeIf { it >= 0 },
                    relativePath = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH).takeIf { it >= 0 },
                )
        }

        private enum class CollectionKind {
            FILES,
            DOWNLOADS,
            ;

            val uri: Uri
                get() =
                    when (this) {
                        FILES -> {
                            MediaStore.Files.getContentUri("external")
                        }

                        DOWNLOADS -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI
                            } else {
                                error("Downloads collection requires API 29+")
                            }
                        }
                    }
        }

        private fun String.escapeLikeWildcards(): String = replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
