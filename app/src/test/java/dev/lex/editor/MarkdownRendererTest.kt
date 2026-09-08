package dev.lex.editor

import dev.lex.editor.format.MarkdownRenderer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRendererTest {

    @Test
    fun `renders headings`() {
        val html = MarkdownRenderer.toHtml("# Title\n\n## Section\n")
        assertTrue(html, html.contains("<h1>Title</h1>"))
        assertTrue(html, html.contains("<h2>Section</h2>"))
    }

    @Test
    fun `renders lists`() {
        val html = MarkdownRenderer.toHtml("- one\n- two\n")
        assertTrue(html, html.contains("<ul>"))
        assertTrue(html, html.contains("<li>one</li>"))
        assertTrue(html, html.contains("<li>two</li>"))
    }

    @Test
    fun `renders fenced code blocks with a language class`() {
        val html = MarkdownRenderer.toHtml("```kotlin\nval x = 1\n```\n")
        assertTrue(html, html.contains("<pre>"))
        assertTrue(html, html.contains("""<code class="language-kotlin">"""))
        assertTrue(html, html.contains("val x = 1"))
    }

    @Test
    fun `renders GFM tables`() {
        val markdown = """
            | name | qty |
            | ---- | --- |
            | tea  | 2   |
        """.trimIndent()
        val html = MarkdownRenderer.toHtml(markdown)
        assertTrue(html, html.contains("<table>"))
        assertTrue(html, html.contains("<thead>"))
        assertTrue(html, html.contains("<th>name</th>"))
        assertTrue(html, html.contains("<td>tea</td>"))
        assertTrue(html, html.contains("<td>2</td>"))
    }

    @Test
    fun `autolinks bare URLs`() {
        val html = MarkdownRenderer.toHtml("Visit https://example.com for more.")
        assertTrue(html, html.contains("""<a href="https://example.com">"""))
    }

    @Test
    fun `escapes raw HTML-unsafe text in code`() {
        val html = MarkdownRenderer.toHtml("`a < b & c`")
        assertTrue(html, html.contains("<code>a &lt; b &amp; c</code>"))
    }

    @Test
    fun `returns a fragment not a document`() {
        val html = MarkdownRenderer.toHtml("# Title\n")
        assertFalse(html, html.contains("<html", ignoreCase = true))
        assertFalse(html, html.contains("<body", ignoreCase = true))
        assertFalse(html, html.contains("doctype", ignoreCase = true))
    }

    @Test
    fun `handles empty input`() {
        assertTrue(MarkdownRenderer.toHtml("").isEmpty())
    }
}
