package dev.lex.editor.model

import android.net.Uri

/**
 * Everything Lex needs to write a document back exactly the way it found it:
 * same charset, same BOM, same line terminator.
 */
data class DocumentMeta(
    val uri: Uri,
    val displayName: String,
    val type: FileType,
    val charsetName: String,
    val hasBom: Boolean,
    val lineEnding: LineEnding,
    val sizeBytes: Long,
)

/** [text] is always normalised to LF; [DocumentMeta.lineEnding] remembers the original. */
data class LoadedDocument(
    val meta: DocumentMeta,
    val text: String,
)

/** One row in the file browser. */
data class DirEntry(
    val uri: Uri,
    val documentId: String,
    val name: String,
    val mimeType: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
) {
    val type: FileType get() = if (isDirectory) FileType.PLAIN else FileType.resolve(name, mimeType)
}

/** A folder the user granted persistent access to. */
data class RecentTree(
    val treeUri: Uri,
    val label: String,
    val lastOpenedAt: Long,
)
