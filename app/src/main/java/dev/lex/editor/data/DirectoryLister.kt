package dev.lex.editor.data

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import dev.lex.editor.model.DirEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lists the contents of a folder the user granted through `ACTION_OPEN_DOCUMENT_TREE`.
 *
 * Everything here is deliberately built on raw [DocumentsContract] queries rather than
 * `DocumentFile`: `DocumentFile.listFiles()` performs one binder round trip per child to fill in
 * name, size and type, which turns a 500-file folder into 500 IPCs and a visibly janky browser.
 * A single cursor over the children URI returns the same information in one call.
 */
class DirectoryLister(private val context: Context) {

    suspend fun listChildren(
        treeUri: Uri,
        parentDocumentId: String?,
    ): List<DirEntry> = withContext(Dispatchers.IO) {
        try {
            // Inside the try: a URI that is not a tree throws here, and the browser should show an
            // empty folder rather than take the app down.
            val parentId = parentDocumentId ?: DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)

            val entries = ArrayList<DirEntry>()
            context.contentResolver.query(childrenUri, CHILD_PROJECTION, null, null, null)
                ?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val documentId = cursor.getString(COL_ID) ?: continue
                        val mimeType = cursor.getString(COL_MIME).orEmpty()
                        val name = cursor.getString(COL_NAME)?.takeIf { it.isNotEmpty() }
                            ?: documentId.substringAfterLast('/')

                        entries += DirEntry(
                            // A child URI is not addressable on its own; it has to be rebuilt
                            // against the tree so the grant travels with it.
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                            documentId = documentId,
                            name = name,
                            mimeType = mimeType,
                            isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR,
                            sizeBytes = cursor.longAt(COL_SIZE),
                            lastModified = cursor.longAt(COL_MODIFIED),
                        )
                    }
                }
            entries.sortedWith(ENTRY_ORDER)
        } catch (e: Exception) {
            // A revoked grant, a deleted folder or a provider that died all land here; the browser
            // shows an empty folder rather than crashing.
            Log.w(TAG, "listChildren failed for $treeUri / $parentDocumentId", e)
            emptyList()
        }
    }

    /** The document id of the tree's own root, i.e. the starting point for [listChildren]. */
    fun rootDocumentId(treeUri: Uri): String = DocumentsContract.getTreeDocumentId(treeUri)

    /**
     * Human-readable name of the granted folder, for the recents list and the browser title.
     * Falls back to the URI's last path segment (e.g. `primary:Documents/Notes`) when the provider
     * will not answer.
     */
    fun displayNameOf(treeUri: Uri): String {
        val rootUri = runCatching {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId(treeUri))
        }.getOrNull() ?: return fallbackLabel(treeUri)

        val queried = runCatching {
            context.contentResolver.query(rootUri, NAME_PROJECTION, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
        }.getOrElse {
            Log.w(TAG, "displayNameOf failed for $treeUri", it)
            null
        }

        return queried?.takeIf { it.isNotBlank() } ?: fallbackLabel(treeUri)
    }

    private companion object {
        const val TAG = "Lex"

        val CHILD_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

        // Column order is fixed by CHILD_PROJECTION, so index lookups are unnecessary.
        const val COL_ID = 0
        const val COL_NAME = 1
        const val COL_MIME = 2
        const val COL_SIZE = 3
        const val COL_MODIFIED = 4

        val NAME_PROJECTION = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

        /** Folders above files, then case-insensitive by name — the usual file-manager order. */
        val ENTRY_ORDER: Comparator<DirEntry> =
            compareByDescending<DirEntry> { it.isDirectory }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }

        fun fallbackLabel(treeUri: Uri): String =
            treeUri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringAfterLast(':')
                ?.takeIf { it.isNotBlank() }
                ?: treeUri.toString()
    }
}

/** Providers are allowed to leave SIZE and LAST_MODIFIED unset; treat those as zero. */
private fun Cursor.longAt(index: Int): Long = if (isNull(index)) 0L else getLong(index)
