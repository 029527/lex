package dev.lex.editor

import dev.lex.editor.format.HtmlFormatter
import dev.lex.editor.model.FormatResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlFormatterTest {

    private fun formatted(text: String): String {
        val result = HtmlFormatter.format(text)
        assertTrue("expected success but was $result", result is FormatResult.Success)
        return (result as FormatResult.Success).text
    }

    @Test
    fun `indents a simple document`() {
        val out = formatted("<html><head><title>T</title></head><body><div><p>Hi</p></div></body></html>")
        assertTrue(out, out.contains("<html>"))
        assertTrue(out, Regex("\\n\\s+<head>").containsMatchIn(out))
        assertTrue(out, Regex("\\n\\s+<body>").containsMatchIn(out))
        assertTrue(out, out.contains("<title>T</title>"))
        assertTrue(out, out.contains("<p>Hi</p>"))
    }

    @Test
    fun `keeps a doctype document whole`() {
        val out = formatted("<!DOCTYPE html><html><body><p>Hi</p></body></html>")
        assertTrue(out, out.contains("<!doctype html>", ignoreCase = true))
        assertTrue(out, out.contains("<html>"))
    }

    @Test
    fun `a fragment stays a fragment`() {
        val out = formatted("<div><p>Hi</p><span>there</span></div>")
        assertFalse(out, out.contains("<html", ignoreCase = true))
        assertFalse(out, out.contains("<body", ignoreCase = true))
        assertFalse(out, out.contains("<head", ignoreCase = true))
        assertFalse(out, out.contains("doctype", ignoreCase = true))
        assertTrue(out, out.contains("<div>"))
        assertTrue(out, out.contains("<p>Hi</p>"))
    }

    @Test
    fun `pre content is preserved verbatim`() {
        val out = formatted("<div><pre>keep   these   spaces\n   and this indent</pre></div>")
        assertTrue(out, out.contains("keep   these   spaces"))
        assertTrue(out, out.contains("\n   and this indent"))
    }

    @Test
    fun `textarea content is preserved verbatim`() {
        val out = formatted("<div><textarea>a   b\n  c</textarea></div>")
        assertTrue(out, out.contains("a   b\n  c"))
    }

    @Test
    fun `script body is not reformatted`() {
        val out = formatted("<div><script>var a = {  x:1  };\nvar b   = 2;</script></div>")
        assertTrue(out, out.contains("var a = {  x:1  };\nvar b   = 2;"))
    }

    @Test
    fun `output is LF-only`() {
        val out = formatted("<div>\r\n<p>Hi</p>\r\n</div>")
        assertFalse(out, out.contains('\r'))
    }

    @Test
    fun `rejects blank input`() {
        assertEquals(FormatResult.Failure("Nothing to format"), HtmlFormatter.format(""))
        assertEquals(FormatResult.Failure("Nothing to format"), HtmlFormatter.format("  \n\t "))
    }
}
