package dev.lex.editor.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import dev.lex.editor.model.RecentTree
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remembers the folders the user granted persistent access to, so Lex can offer them again on the
 * next launch instead of sending the user back through the system folder picker.
 *
 * Backed by a single SharedPreferences string holding a small JSON array — a handful of rows never
 * justifies a database, and `org.json` is part of the platform.
 */
class RecentTreeStore(private val context: Context) {

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Adds or refreshes [treeUri], moving it to the front of the list. */
    fun remember(treeUri: Uri, label: String) {
        val key = treeUri.toString()
        val now = System.currentTimeMillis()
        val updated = buildList {
            add(RecentTree(treeUri, label, now))
            // Dedupe by URI: re-opening a folder promotes it rather than duplicating it.
            addAll(read().filter { it.treeUri.toString() != key })
        }.take(MAX_ENTRIES)
        write(updated)
    }

    /** Most recently opened first. */
    fun all(): List<RecentTree> = read()

    fun forget(treeUri: Uri) {
        val key = treeUri.toString()
        val remaining = read().filter { it.treeUri.toString() != key }
        write(remaining)
    }

    // ---------------------------------------------------------------- persistence

    private fun read(): List<RecentTree> {
        val raw = prefs.getString(KEY_TREES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val out = ArrayList<RecentTree>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val uri = obj.optString(FIELD_URI).takeIf { it.isNotEmpty() } ?: continue
                out += RecentTree(
                    treeUri = Uri.parse(uri),
                    label = obj.optString(FIELD_LABEL).takeIf { it.isNotEmpty() } ?: uri,
                    lastOpenedAt = obj.optLong(FIELD_LAST_OPENED, 0L),
                )
            }
            // Stored newest-first already; sorting keeps the contract honest if the file was
            // written by an older build or hand-edited.
            out.sortedByDescending { it.lastOpenedAt }.take(MAX_ENTRIES)
        } catch (e: Exception) {
            // Corrupt preferences must not be able to stop the app from starting.
            Log.w(TAG, "Discarding unreadable recent-tree list", e)
            emptyList()
        }
    }

    private fun write(entries: List<RecentTree>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put(FIELD_URI, entry.treeUri.toString())
                    .put(FIELD_LABEL, entry.label)
                    .put(FIELD_LAST_OPENED, entry.lastOpenedAt)
            )
        }
        prefs.edit().putString(KEY_TREES, array.toString()).apply()
    }

    private companion object {
        const val TAG = "Lex"
        const val PREFS_NAME = "lex_recent"
        const val KEY_TREES = "trees"
        const val MAX_ENTRIES = 10

        const val FIELD_URI = "uri"
        const val FIELD_LABEL = "label"
        const val FIELD_LAST_OPENED = "lastOpenedAt"
    }
}
