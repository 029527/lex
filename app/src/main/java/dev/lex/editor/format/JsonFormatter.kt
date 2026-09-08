package dev.lex.editor.format

import dev.lex.editor.model.FormatResult

/**
 * Re-indents JSON by scanning characters rather than parsing into a model and
 * re-serialising.
 *
 * A parse/re-serialise round trip is lossy in ways users notice: number literals
 * get normalised (`1.50` -> `1.5`, `1e3` -> `1000.0`, big integers overflow into
 * doubles), and most map implementations do not promise to keep object keys in
 * document order. This scanner copies every token through verbatim -- strings
 * with their escapes, numbers with their exact spelling -- and only rewrites the
 * whitespace *between* tokens.
 *
 * The scan is still validating: it runs a small state machine over the token
 * stream so malformed input fails with a located message instead of silently
 * producing garbage.
 */
object JsonFormatter {

    /** Pretty-prints [text] with [indent] spaces per level (clamped to 0..8). LF-only, no trailing newline. */
    fun format(text: String, indent: Int = 2): FormatResult =
        scan(text, pretty = true, indentWidth = indent.coerceIn(0, 8))

    /** Strips every byte of insignificant whitespace. Strings are still copied verbatim. */
    fun minify(text: String): FormatResult =
        scan(text, pretty = false, indentWidth = 0)

    private fun scan(text: String, pretty: Boolean, indentWidth: Int): FormatResult {
        if (text.isBlank()) return FormatResult.Failure("Nothing to format")
        return try {
            FormatResult.Success(Scanner(text, pretty, indentWidth).scanAll())
        } catch (e: JsonSyntaxException) {
            FormatResult.Failure(e.message ?: "Invalid JSON")
        }
    }
}

private class JsonSyntaxException(message: String) : Exception(message)

/** What the state machine will accept next. */
private enum class Expect {
    /** A value must follow (start of document, after `:` or after `,` in an array). */
    VALUE,

    /** A value, or the `]` of an empty/just-opened array. */
    VALUE_OR_CLOSE,

    /** A quoted key must follow (after `,` in an object). */
    KEY,

    /** A key, or the `}` of an empty/just-opened object. */
    KEY_OR_CLOSE,

    /** The `:` between a key and its value. */
    COLON,

    /** A `,` or the closing bracket of the enclosing container. */
    COMMA_OR_CLOSE,

    /** The top-level value is complete; only whitespace may follow. */
    END,
}

private class Frame(val isObject: Boolean, val line: Int, val col: Int)

private class Scanner(
    private val src: String,
    private val pretty: Boolean,
    indentWidth: Int,
) {
    private val out = StringBuilder(src.length + 64)
    private val unit = " ".repeat(indentWidth)
    private val stack = ArrayList<Frame>()

    private var expect = Expect.VALUE
    private var i = 0
    private var line = 1
    private var lineStart = 0

    /** A newline + indent is owed before the next token (set after `{`, `[` and `,`). */
    private var needIndent = false

    /** The last token emitted was an opening bracket with nothing after it yet. */
    private var pendingOpen = false

    fun scanAll(): String {
        while (true) {
            skipWhitespace()
            if (i >= src.length) break
            val c = src[i]
            if (expect == Expect.END) {
                fail("Unexpected '$c' after the end of the document at line $line, column ${col()}")
            }
            when (c) {
                '{' -> open(isObject = true)
                '[' -> open(isObject = false)
                '}' -> close(isObject = true)
                ']' -> close(isObject = false)
                ',' -> comma()
                ':' -> colon()
                '"' -> stringToken()
                else -> bareToken()
            }
        }
        stack.lastOrNull()?.let { frame ->
            val bracket = if (frame.isObject) '{' else '['
            fail("Unclosed '$bracket' at line ${frame.line}, column ${frame.col}")
        }
        if (expect != Expect.END) {
            fail("Unexpected end of input at line $line, column ${col()}")
        }
        return out.toString()
    }

    private fun col(): Int = i - lineStart + 1

    private fun fail(message: String): Nothing = throw JsonSyntaxException(message)

    private fun skipWhitespace() {
        while (i < src.length) {
            when (src[i]) {
                '\n' -> {
                    i++
                    line++
                    lineStart = i
                }
                ' ', '\t', '\r' -> i++
                else -> return
            }
        }
    }

    /** Pays off any owed newline + indent before a token is written. */
    private fun beforeToken() {
        if (pretty && needIndent) {
            out.append('\n')
            repeat(stack.size) { out.append(unit) }
        }
        needIndent = false
        pendingOpen = false
    }

    private fun open(isObject: Boolean) {
        if (expect != Expect.VALUE && expect != Expect.VALUE_OR_CLOSE) {
            fail("Unexpected '${src[i]}' at line $line, column ${col()}")
        }
        beforeToken()
        out.append(src[i])
        stack.add(Frame(isObject, line, col()))
        i++
        expect = if (isObject) Expect.KEY_OR_CLOSE else Expect.VALUE_OR_CLOSE
        needIndent = true
        pendingOpen = true
    }

    private fun close(isObject: Boolean) {
        val closer = src[i]
        val frame = stack.lastOrNull()
        val allowed = expect == Expect.COMMA_OR_CLOSE ||
            (isObject && expect == Expect.KEY_OR_CLOSE) ||
            (!isObject && expect == Expect.VALUE_OR_CLOSE)
        if (!allowed || frame == null || frame.isObject != isObject) {
            fail("Unexpected '$closer' at line $line, column ${col()}")
        }
        stack.removeAt(stack.size - 1)
        if (pendingOpen) {
            // `{` immediately followed by `}` collapses to `{}` -- never an empty indented line.
            out.append(closer)
        } else {
            if (pretty) {
                out.append('\n')
                repeat(stack.size) { out.append(unit) }
            }
            out.append(closer)
        }
        needIndent = false
        pendingOpen = false
        i++
        afterValue()
    }

    private fun comma() {
        if (expect != Expect.COMMA_OR_CLOSE || stack.isEmpty()) {
            fail("Unexpected ',' at line $line, column ${col()}")
        }
        out.append(',')
        i++
        expect = if (stack.last().isObject) Expect.KEY else Expect.VALUE
        needIndent = true
        pendingOpen = false
    }

    private fun colon() {
        if (expect != Expect.COLON) {
            fail("Unexpected ':' at line $line, column ${col()}")
        }
        out.append(if (pretty) ": " else ":")
        i++
        expect = Expect.VALUE
        pendingOpen = false
    }

    private fun stringToken() {
        val isKey = expect == Expect.KEY || expect == Expect.KEY_OR_CLOSE
        if (!isKey && expect != Expect.VALUE && expect != Expect.VALUE_OR_CLOSE) {
            fail("Unexpected '\"' at line $line, column ${col()}")
        }
        val startLine = line
        val startCol = col()
        val start = i
        i++ // opening quote
        var escaped = false
        var closed = false
        while (i < src.length) {
            val c = src[i]
            when {
                c == '\n' -> {
                    i++
                    line++
                    lineStart = i
                    escaped = false
                }
                escaped -> {
                    escaped = false
                    i++
                }
                c == '\\' -> {
                    escaped = true
                    i++
                }
                c == '"' -> {
                    i++
                    closed = true
                }
                else -> i++
            }
            if (closed) break
        }
        if (!closed) {
            fail("Unterminated string starting at line $startLine, column $startCol")
        }
        beforeToken()
        // Verbatim: quotes, escapes and all. Nothing inside a string is ever rewritten.
        out.append(src, start, i)
        if (isKey) expect = Expect.COLON else afterValue()
    }

    /** A number, `true`, `false` or `null` -- anything not delimited by quotes or punctuation. */
    private fun bareToken() {
        if (expect != Expect.VALUE && expect != Expect.VALUE_OR_CLOSE) {
            fail("Unexpected '${src[i]}' at line $line, column ${col()}")
        }
        val start = i
        val startCol = col()
        while (i < src.length && isBareChar(src[i])) i++
        if (i == start) {
            fail("Unexpected '${src[i]}' at line $line, column $startCol")
        }
        val token = src.substring(start, i)
        if (token !in LITERALS && !NUMBER.matches(token)) {
            fail("Invalid value '$token' at line $line, column $startCol")
        }
        beforeToken()
        out.append(token)
        afterValue()
    }

    private fun afterValue() {
        expect = if (stack.isEmpty()) Expect.END else Expect.COMMA_OR_CLOSE
    }

    private fun isBareChar(c: Char): Boolean =
        c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z' || c == '-' || c == '+' || c == '.'

    private companion object {
        val LITERALS = setOf("true", "false", "null")

        // Deliberately a shade looser than RFC 8259 (leading zeros are tolerated):
        // a formatter should not refuse a file over a detail no reader minds.
        val NUMBER = Regex("""-?\d+(\.\d+)?([eE][+-]?\d+)?""")
    }
}
