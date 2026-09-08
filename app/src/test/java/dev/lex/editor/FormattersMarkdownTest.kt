package dev.lex.editor

import dev.lex.editor.format.Formatters
import dev.lex.editor.model.FileType
import dev.lex.editor.model.FormatResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Markdown tidy is only allowed to touch whitespace that carries no meaning.
 * These cases pin down the two places where trailing whitespace *is* meaningful.
 */
class FormattersMarkdownTest {

    private fun tidy(input: String): String {
        val result = Formatters.format(FileType.MARKDOWN, input)
        assertTrue("expected success, got $result", result is FormatResult.Success)
        return (result as FormatResult.Success).text
    }

    @Test
    fun `hard line break is preserved`() {
        assertEquals("first  \nsecond", tidy("first  \nsecond"))
    }

    @Test
    fun `excess trailing spaces collapse to a canonical hard break`() {
        assertEquals("first  \nsecond", tidy("first     \nsecond"))
    }

    @Test
    fun `a single trailing space is not a hard break and is removed`() {
        assertEquals("first\nsecond", tidy("first \nsecond"))
    }

    @Test
    fun `trailing whitespace inside a fenced code block is content`() {
        val src = "text\n\n```\nval a = 1   \n\tindented\t\n```\n\nmore"
        assertEquals(src.trimEnd(), tidy(src))
    }

    @Test
    fun `tilde fences are honoured`() {
        val src = "~~~\nkeep   this   \n~~~"
        assertEquals(src, tidy(src))
    }

    @Test
    fun `blank line runs inside a fence are untouched`() {
        val src = "```\na\n\n\n\n\nb\n```"
        assertEquals(src, tidy(src))
    }

    @Test
    fun `four blank lines outside a fence collapse to one`() {
        assertEquals("a\n\nb", tidy("a\n\n\n\n\nb"))
    }

    @Test
    fun `two blank lines are left alone`() {
        assertEquals("a\n\n\nb", tidy("a\n\n\nb"))
    }
}
