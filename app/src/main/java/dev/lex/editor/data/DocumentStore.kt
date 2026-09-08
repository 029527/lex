package dev.lex.editor.data

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import dev.lex.editor.model.DirEntry
import dev.lex.editor.model.DocumentMeta
import dev.lex.editor.model.FileType
import dev.lex.editor.model.LineEnding
import dev.lex.editor.model.LoadedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale

/**
 * Reads and writes documents behind Storage Access Framework URIs.
 *
 * The editor works exclusively in LF text; this class owns the round trip between that and the
 * bytes on disk — charset, byte-order mark and line terminator are captured on load and replayed
 * on save so that opening and saving an untouched file is a no-op.
 */
class DocumentStore(private val context: Context) {

    /**
     * Loads [uri] into memory and normalises it for editing.
     *
     * Every failure mode (missing file, revoked permission, oversized document) is reported as a
     * [Result.failure] whose message is safe to show to the user.
     */
    suspend fun load(uri: Uri): Result<LoadedDocument> = withContext(Dispatchers.IO) {
        runCatching {
            val entry = queryEntry(uri)

            // Cheap rejection first: most providers report SIZE, so we can bail before reading.
            val declaredSize = entry?.sizeBytes ?: 0L
            if (declaredSize > EncodingDetector.MAX_FILE_BYTES) throw tooLarge(declaredSize)

            val bytes = context.contentResolver.openInputStream(uri)?.use { readCapped(it) }
                ?: throw IOException("This file could not be opened for reading.")

            val detected = EncodingDetector.detect(bytes)
            val decoded = String(
                bytes,
                detected.bomLength,
                bytes.size - detected.bomLength,
                EncodingDetector.charsetOf(detected.charsetName),
            )

            // Remember the original terminator before flattening, so save can restore it.
            val lineEnding = LineEnding.detect(decoded)
            val text = normaliseToLf(decoded)

            val name = entry?.name ?: fallbackName(uri)
            val mimeType = entry?.mimeType ?: context.contentResolver.getType(uri).orEmpty()

            LoadedDocument(
                meta = DocumentMeta(
                    uri = uri,
                    displayName = name,
                    type = FileType.resolve(name, mimeType),
                    charsetName = detected.charsetName,
                    hasBom = detected.hasBom,
                    lineEnding = lineEnding,
                    sizeBytes = bytes.size.toLong(),
                ),
                text = text,
            )
        }
    }

    /**
     * Writes [text] (LF-normalised) back to `meta.uri` in the document's original shape.
     *
     * The full payload is encoded before anything is opened: a charset failure must never leave a
     * half-written file behind.
     */
    suspend fun save(meta: DocumentMeta, text: String): Result<DocumentMeta> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = restoreLineEndings(text, meta.lineEnding)
                    .toByteArray(EncodingDetector.charsetOf(meta.charsetName))
                val bom = if (meta.hasBom) EncodingDetector.bomFor(meta.charsetName) else ByteArray(0)
                val payload = if (bom.isEmpty()) body else bom + body

                writeTruncating(meta.uri, payload)
                meta.copy(sizeBytes = payload.size.toLong())
            }
        }

    /**
     * One-row metadata lookup for a document URI. Returns null when the provider knows nothing
     * about it (deleted file, revoked grant, non-document URI).
     */
    suspend fun queryEntry(uri: Uri): DirEntry? = withContext(Dispatchers.IO) {
        // A null projection lets the same code serve SAF document URIs and the plainer
        // openable URIs that arrive from share intents; columns are read defensively below.
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null

                // COLUMN_DISPLAY_NAME / COLUMN_SIZE are the OpenableColumns names, so this also
                // covers the plain content URIs that arrive from share intents.
                val name = cursor.stringOrNull(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    ?: fallbackName(uri)
                val mimeType = cursor.stringOrNull(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    ?: context.contentResolver.getType(uri).orEmpty()
                val documentId = cursor.stringOrNull(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    ?: runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
                    ?: ""

                DirEntry(
                    uri = uri,
                    documentId = documentId,
                    name = name,
                    mimeType = mimeType,
                    isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR,
                    sizeBytes = cursor.longOrNull(DocumentsContract.Document.COLUMN_SIZE) ?: 0L,
                    lastModified = cursor.longOrNull(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                        ?: 0L,
                )
            }
        }.onFailure { Log.w(TAG, "queryEntry failed for $uri", it) }.getOrNull()
    }

    // ---------------------------------------------------------------- writing

    /**
     * Opens [uri], truncates it and writes [payload].
     *
     * Truncation is done by hand for a reason: the "t" mode flag is advisory and a number of
     * DocumentsProviders (cloud backends especially) ignore it, so writing a shorter document over
     * a longer one would leave the previous tail dangling at the end of the file.
     */
    private fun writeTruncating(uri: Uri, payload: ByteArray) {
        val resolver = context.contentResolver
        // Not every provider accepts a read-write mode; the ones that don't throw rather than
        // return null, so treat both the same and fall back to a plain output stream.
        val pfd = try {
            resolver.openFileDescriptor(uri, "rwt")
        } catch (e: Exception) {
            Log.w(TAG, "No rwt descriptor for $uri, falling back to a stream", e)
            null
        }

        if (pfd != null) {
            pfd.use { descriptor ->
                // Intentionally not closed: FileOutputStream.close() would close the descriptor
                // that ParcelFileDescriptor.close() also owns, and the resulting double close can
                // hit an unrelated file that has since inherited the same fd number.
                val out = FileOutputStream(descriptor.fileDescriptor)

                // "rwt" already asks the provider to truncate, but not all of them honour it, so
                // truncate explicitly too. If the descriptor is not seekable this throws -- and it
                // must not be swallowed, because writing a shorter document into an untruncated
                // file leaves the tail of the old one behind. Fall back to the stream path, which
                // opens a fresh "wt" stream, instead.
                val truncated = try {
                    out.channel.truncate(0L)
                    true
                } catch (e: IOException) {
                    Log.w(TAG, "Descriptor for $uri is not truncatable, falling back", e)
                    false
                }

                if (truncated) {
                    out.write(payload)
                    out.flush()
                    try {
                        descriptor.fileDescriptor.sync()
                    } catch (e: IOException) {
                        // Several providers back a document with a pipe or a network stream that
                        // cannot be fsynced. The bytes are already handed over, so this is not a
                        // save failure.
                        Log.w(TAG, "fsync not supported for $uri", e)
                    }
                    return
                }
            }
        }

        // Write-only or non-seekable providers can't give us a truncatable descriptor; "wt" is
        // the best ask. A provider that ignores it here would corrupt the file, but there is no
        // further fallback available, and in practice DocumentsProvider implementations honour it.
        resolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(payload)
            out.flush()
        } ?: throw IOException("This file could not be opened for writing.")
    }

    // ---------------------------------------------------------------- helpers

    private fun readCapped(input: InputStream): ByteArray {
        val limit = EncodingDetector.MAX_FILE_BYTES
        val buffer = ByteArray(BUFFER_SIZE)
        val sink = ByteArrayOutputStream(BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            // Providers that report no size still must not be able to exhaust the heap.
            if (total > limit) throw tooLarge(total)
            sink.write(buffer, 0, read)
        }
        return sink.toByteArray()
    }

    private fun tooLarge(size: Long) = IllegalArgumentException(
        "This file is ${formatBytes(size)}, which is larger than the " +
            "${formatBytes(EncodingDetector.MAX_FILE_BYTES)} Lex can open."
    )

    private companion object {
        const val TAG = "Lex"
        const val BUFFER_SIZE = 8 * 1024

        fun formatBytes(size: Long): String = when {
            size >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024.0))
            size >= 1024L -> String.format(Locale.US, "%.1f KB", size / 1024.0)
            else -> "$size bytes"
        }

        /** Collapses CRLF and lone CR to LF; the editor only ever sees LF. */
        fun normaliseToLf(text: String): String =
            if (text.indexOf('\r') < 0) text else text.replace("\r\n", "\n").replace('\r', '\n')

        fun restoreLineEndings(lfText: String, lineEnding: LineEnding): String =
            if (lineEnding == LineEnding.LF) lfText
            else lfText.replace("\n", lineEnding.sequence)

        fun fallbackName(uri: Uri): String =
            uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.takeIf { it.isNotBlank() }
                ?: "Untitled"
    }
}

private fun Cursor.stringOrNull(column: String): String? {
    val index = getColumnIndex(column)
    return if (index >= 0 && !isNull(index)) getString(index)?.takeIf { it.isNotEmpty() } else null
}

private fun Cursor.longOrNull(column: String): Long? {
    val index = getColumnIndex(column)
    return if (index >= 0 && !isNull(index)) getLong(index) else null
}
