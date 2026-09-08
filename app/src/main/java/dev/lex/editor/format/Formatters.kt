package dev.lex.editor.format

import dev.lex.editor.model.FileType
import dev.lex.editor.model.FormatResult

/** Single entry point the UI calls: picks the right formatter for a [FileType]. */
object Formatters {

    fun format(type: FileType, text: String): FormatResult = when (type) {
        FileType.JSON -> JsonFormatter.format(text)
        FileType.HTML -> HtmlFormatter.format(text)
        FileType.MARKDOWN -> tidyMarkdown(text)
        FileType.PLAIN -> FormatResult.Unsupported
    }

    /**
     * Deliberately timid.
     *
     * Reflowing Markdown is lossy -- re-wrapping paragraphs, renumbering ordered
     * lists, normalising `*` vs `-` bullets or ATX vs setext headings all rewrite
     * text the author chose on purpose, and diff noise in a prose file is far
     * worse than slightly ragged whitespace. So this only removes whitespace that
     * carries no meaning: trailing spaces and runs of blank lines.
     */
    private fun tidyMarkdown(text: String): FormatResult {
        if (text.isBlank()) return FormatResult.Failure("Nothing to format")

        val normalised = if (text.indexOf('\r') < 0) text else {
            text.replace("\r\n", "\n").replace('\r', '\n')
        }

        val out = StringBuilder(normalised.length)
        var blankRun = 0
        var wroteAnyLine = false
        var fence: String? = null

        for (raw in normalised.splitToSequence('\n')) {
            // Inside a fenced code block every character is content, trailing spaces
            // included, so those lines pass through untouched.
            val fenceMarker = FENCE.matchEntire(raw.trimEnd())?.groupValues?.get(1)
            if (fence != null) {
                if (fenceMarker != null && fenceMarker.startsWith(fence!!.take(1)) &&
                    fenceMarker.length >= fence!!.length
                ) {
                    fence = null
                }
                if (wroteAnyLine) repeat(blankRun + 1) { out.append('\n') }
                blankRun = 0
                out.append(raw)
                wroteAnyLine = true
                continue
            }
            if (fenceMarker != null) fence = fenceMarker

            val line = tidyLine(raw)
            if (line.isEmpty()) {
                blankRun++
                continue
            }
            if (wroteAnyLine) {
                // 3+ consecutive blank lines collapse to a single blank line; one or
                // two are left alone (two is a common, intentional section break).
                val keep = if (blankRun >= 3) 1 else blankRun
                repeat(keep + 1) { out.append('\n') }
            }
            blankRun = 0
            out.append(line)
            wroteAnyLine = true
        }
        // Trailing blank lines are dropped: output is LF-only with no trailing newline.
        return FormatResult.Success(out.toString())
    }

    /**
     * Two or more trailing spaces are a Markdown hard line break, so they are content,
     * not stray whitespace. Excess is normalised to the canonical two rather than
     * stripped, which would silently drop a `<br>` from the rendered output.
     */
    private fun tidyLine(raw: String): String {
        val trimmed = raw.trimEnd()
        if (trimmed.isEmpty()) return ""
        val trailing = raw.length - trimmed.length
        val hardBreak = trailing >= 2 && raw.endsWith("  ")
        return if (hardBreak) "$trimmed  " else trimmed
    }

    /** Opening or closing fence of a code block: three or more backticks or tildes. */
    private val FENCE = Regex("^\\s{0,3}(`{3,}|~{3,}).*$")
}
