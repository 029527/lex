package dev.lex.editor.format

import dev.lex.editor.model.FormatResult
import org.jsoup.Jsoup

/**
 * Pretty-prints HTML with jsoup.
 *
 * jsoup always parses into a *document*, repairing markup as it goes, so a
 * fragment handed to [Jsoup.parse] would come back wearing a full
 * `<html><head><body>` skeleton it never had. To keep a fragment a fragment we
 * sniff for document markers first and, when there are none, parse as a body
 * fragment and emit only the body's inner HTML.
 */
object HtmlFormatter {

    /** True when the source already carries document-level markup we must keep. */
    private val DOCUMENT_MARKER = Regex("""<(!doctype\s|html\b|head\b|body\b)""", RegexOption.IGNORE_CASE)

    fun format(text: String): FormatResult {
        if (text.isBlank()) return FormatResult.Failure("Nothing to format")
        return try {
            val isDocument = DOCUMENT_MARKER.containsMatchIn(text)
            val doc = if (isDocument) Jsoup.parse(text) else Jsoup.parseBodyFragment(text)
            doc.outputSettings()
                .prettyPrint(true)
                .indentAmount(2)
                .outline(false)
            // jsoup's pretty printer leaves whitespace-preserving tags alone: `pre`,
            // `textarea`, `plaintext` and `title` are flagged preserveWhitespace in its
            // tag table, and `script`/`style` bodies are DataNodes emitted verbatim.
            // We must not undo that, so nothing here touches the tree itself.
            val html = if (isDocument) doc.outerHtml() else doc.body().html()
            FormatResult.Success(html.normaliseToLf())
        } catch (e: Exception) {
            FormatResult.Failure(e.message ?: "Could not format HTML")
        }
    }

    private fun String.normaliseToLf(): String =
        if (indexOf('\r') < 0) this else replace("\r\n", "\n").replace('\r', '\n')
}
