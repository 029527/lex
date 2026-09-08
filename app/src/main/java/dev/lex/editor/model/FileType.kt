package dev.lex.editor.model

import androidx.annotation.StringRes
import dev.lex.editor.R

/**
 * The document kinds Lex understands. Everything else falls back to [PLAIN].
 *
 * The label is a string resource rather than a literal: "Plain Text" is the one
 * name here that is a real phrase and has to be translated, while the other three
 * are product names that stay as they are in every language.
 */
enum class FileType(
    @param:StringRes val labelRes: Int,
    val canFormat: Boolean,
    val canPreview: Boolean,
) {
    MARKDOWN(R.string.filetype_markdown, canFormat = true, canPreview = true),
    HTML(R.string.filetype_html, canFormat = true, canPreview = true),
    JSON(R.string.filetype_json, canFormat = true, canPreview = false),
    PLAIN(R.string.filetype_plain, canFormat = false, canPreview = false);

    companion object {
        fun fromFileName(name: String): FileType =
            when (name.substringAfterLast('.', "").lowercase()) {
                "md", "markdown", "mdown", "mkd", "mdx" -> MARKDOWN
                "html", "htm", "xhtml" -> HTML
                "json", "jsonc", "json5" -> JSON
                else -> PLAIN
            }

        /** Extension wins; MIME is only consulted when the name carries no useful suffix. */
        fun resolve(name: String, mimeType: String?): FileType {
            val byName = fromFileName(name)
            if (byName != PLAIN) return byName
            return when (mimeType?.lowercase()?.substringBefore(';')?.trim()) {
                "text/markdown", "text/x-markdown" -> MARKDOWN
                "text/html", "application/xhtml+xml" -> HTML
                "application/json", "text/json" -> JSON
                else -> PLAIN
            }
        }
    }
}
