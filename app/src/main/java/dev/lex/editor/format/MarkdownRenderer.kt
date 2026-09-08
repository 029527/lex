package dev.lex.editor.format

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/**
 * CommonMark -> HTML, with the GFM tables and autolink extensions.
 *
 * The [Parser] and [HtmlRenderer] are built once: construction is the expensive
 * part, and both are immutable and thread-safe once built, so the preview can be
 * re-rendered off the main thread on every keystroke without re-assembling them.
 */
object MarkdownRenderer {

    private val extensions = listOf(TablesExtension.create(), AutolinkExtension.create())

    private val parser: Parser = Parser.builder()
        .extensions(extensions)
        .build()

    private val renderer: HtmlRenderer = HtmlRenderer.builder()
        .extensions(extensions)
        .build()

    /** Returns an HTML *fragment* -- no document skeleton. See [PreviewDocument] for that. */
    fun toHtml(markdown: String): String = renderer.render(parser.parse(markdown))
}
