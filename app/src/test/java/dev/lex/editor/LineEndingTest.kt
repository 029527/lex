package dev.lex.editor

import dev.lex.editor.model.LineEnding
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The editor works in LF and restores the detected terminator on save, so a wrong answer here
 * rewrites every line of the user's file.
 */
class LineEndingTest {

    @Test
    fun `unix newlines are lf`() {
        assertEquals(LineEnding.LF, LineEnding.detect("alpha\nbeta\ngamma\n"))
    }

    @Test
    fun `windows newlines are crlf`() {
        assertEquals(LineEnding.CRLF, LineEnding.detect("alpha\r\nbeta\r\ngamma\r\n"))
    }

    @Test
    fun `classic mac newlines are cr`() {
        assertEquals(LineEnding.CR, LineEnding.detect("alpha\rbeta\rgamma\r"))
    }

    @Test
    fun `crlf wins when it dominates a mixed file`() {
        assertEquals(LineEnding.CRLF, LineEnding.detect("alpha\r\nbeta\r\ngamma\ndelta"))
    }

    @Test
    fun `lf wins when it dominates a mixed file`() {
        assertEquals(LineEnding.LF, LineEnding.detect("alpha\nbeta\ngamma\r\ndelta"))
    }

    @Test
    fun `cr wins when it dominates a mixed file`() {
        assertEquals(LineEnding.CR, LineEnding.detect("alpha\rbeta\rgamma\ndelta"))
    }

    @Test
    fun `a lone cr is not counted as crlf`() {
        // The \r here is followed by 'b', not by \n.
        assertEquals(LineEnding.CR, LineEnding.detect("a\rb"))
    }

    @Test
    fun `text without a newline defaults to lf`() {
        assertEquals(LineEnding.LF, LineEnding.detect("a single line"))
    }

    @Test
    fun `empty text defaults to lf`() {
        assertEquals(LineEnding.LF, LineEnding.detect(""))
    }

    @Test
    fun `trailing cr at end of text is treated as cr`() {
        assertEquals(LineEnding.CR, LineEnding.detect("alpha\r"))
    }
}
