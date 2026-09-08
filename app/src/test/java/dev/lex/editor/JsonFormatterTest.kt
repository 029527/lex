package dev.lex.editor

import dev.lex.editor.format.JsonFormatter
import dev.lex.editor.model.FormatResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonFormatterTest {

    private fun formatted(text: String, indent: Int = 2): String {
        val result = JsonFormatter.format(text, indent)
        assertTrue("expected success but was $result", result is FormatResult.Success)
        return (result as FormatResult.Success).text
    }

    private fun failure(text: String): String {
        val result = JsonFormatter.format(text)
        assertTrue("expected failure but was $result", result is FormatResult.Failure)
        return (result as FormatResult.Failure).message
    }

    @Test
    fun `pretty prints a nested object`() {
        val input = """{"a":{"b":[1,2,{"c":null}]},"d":true}"""
        assertEquals(
            """
            {
              "a": {
                "b": [
                  1,
                  2,
                  {
                    "c": null
                  }
                ]
              },
              "d": true
            }
            """.trimIndent(),
            formatted(input),
        )
    }

    @Test
    fun `honours the indent width and clamps it`() {
        assertEquals("{\n    \"a\": 1\n}", formatted("""{"a":1}""", indent = 4))
        assertEquals("{\n\"a\": 1\n}", formatted("""{"a":1}""", indent = 0))
        // 99 clamps to 8, not to something absurd.
        assertEquals("{\n" + " ".repeat(8) + "\"a\": 1\n}", formatted("""{"a":1}""", indent = 99))
    }

    @Test
    fun `preserves key order`() {
        val input = """{"z":1,"a":2,"m":3,"b":4}"""
        val out = formatted(input)
        assertEquals(
            listOf("\"z\"", "\"a\"", "\"m\"", "\"b\""),
            Regex("\"[a-z]\"").findAll(out).map { it.value }.toList(),
        )
    }

    @Test
    fun `preserves number literals exactly`() {
        val out = formatted("""{"a":1.50,"b":1e3,"c":-0.0,"d":1E+10,"e":12345678901234567890}""")
        assertTrue(out, out.contains("\"a\": 1.50"))
        assertTrue(out, out.contains("\"b\": 1e3"))
        assertTrue(out, out.contains("\"c\": -0.0"))
        assertTrue(out, out.contains("\"d\": 1E+10"))
        assertTrue(out, out.contains("\"e\": 12345678901234567890"))
    }

    @Test
    fun `never touches the inside of a string`() {
        // The value contains braces and escaped quotes; none of it is structure.
        val input = """{"a":"}{ \"x\" "}"""
        assertEquals("{\n  \"a\": \"}{ \\\"x\\\" \"\n}", formatted(input))
    }

    @Test
    fun `a trailing backslash escape does not swallow the closing quote`() {
        val input = """{"path":"C:\\"}"""
        assertEquals("{\n  \"path\": \"C:\\\\\"\n}", formatted(input))
    }

    @Test
    fun `collapses empty objects and arrays`() {
        assertEquals(
            "{\n  \"a\": {},\n  \"b\": [],\n  \"c\": [\n    {}\n  ]\n}",
            formatted("""{"a":{ },"b":[
            ],"c":[{}]}"""),
        )
    }

    @Test
    fun `minify round-trips`() {
        val compact = """{"a":{"b":[1,2,{"c":null}]},"d":true,"e":"x, y: z"}"""
        val pretty = formatted(compact)
        val result = JsonFormatter.minify(pretty)
        assertTrue("expected success but was $result", result is FormatResult.Success)
        assertEquals(compact, (result as FormatResult.Success).text)
    }

    @Test
    fun `minify keeps whitespace inside strings`() {
        val result = JsonFormatter.minify("""{ "a" : "  spaced  " }""")
        assertEquals("""{"a":"  spaced  "}""", (result as FormatResult.Success).text)
    }

    @Test
    fun `formats a top-level array and a bare value`() {
        assertEquals("[\n  1,\n  2\n]", formatted("[1, 2]"))
        assertEquals("\"hello\"", formatted(""" "hello" """))
    }

    @Test
    fun `output is LF-only with no trailing newline`() {
        val out = formatted("{\r\n  \"a\": 1\r\n}")
        assertEquals("{\n  \"a\": 1\n}", out)
        assertTrue(!out.contains('\r'))
        assertTrue(!out.endsWith("\n"))
    }

    @Test
    fun `rejects blank input`() {
        assertEquals("Nothing to format", failure("   \n  "))
        assertEquals("Nothing to format", failure(""))
    }

    @Test
    fun `rejects an unclosed bracket`() {
        val message = failure("""{"a": 1""")
        assertTrue(message, message.contains("line"))
        assertTrue(message, message.contains("Unclosed"))
    }

    @Test
    fun `rejects a mismatched bracket`() {
        val message = failure("""[1, 2}""")
        assertTrue(message, message.contains("line"))
        assertTrue(message, message.contains("'}'"))
    }

    @Test
    fun `rejects an unterminated string`() {
        val message = failure("{\n  \"a\": \"oops\n}")
        assertTrue(message, message.contains("line"))
        assertTrue(message, message.contains("Unterminated string"))
        // The report points at the opening quote on line 2, not at the end of input.
        assertTrue(message, message.contains("line 2"))
    }

    @Test
    fun `rejects trailing content after the top-level value`() {
        val message = failure("""{"a": 1} []""")
        assertTrue(message, message.contains("line"))
    }

    @Test
    fun `rejects a bare word that is not a JSON literal`() {
        val message = failure("""{"a": tru}""")
        assertTrue(message, message.contains("line"))
    }

    @Test
    fun `rejects a missing colon and a stray comma`() {
        assertTrue(failure("""{"a" 1}""").contains("line"))
        assertTrue(failure("""[1,,2]""").contains("line"))
        assertTrue(failure("""[1,]""").contains("line"))
    }
}
