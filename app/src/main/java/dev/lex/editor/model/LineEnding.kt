package dev.lex.editor.model

/** Line terminator of the file on disk. Lex edits in LF and restores this on save. */
enum class LineEnding(val label: String, val sequence: String) {
    LF("LF", "\n"),
    CRLF("CRLF", "\r\n"),
    CR("CR", "\r");

    companion object {
        /** Picks the dominant terminator; defaults to [LF] for files with no line break. */
        fun detect(text: String): LineEnding {
            var crlf = 0
            var lf = 0
            var cr = 0
            var i = 0
            while (i < text.length) {
                when (text[i]) {
                    '\r' -> if (i + 1 < text.length && text[i + 1] == '\n') { crlf++; i++ } else cr++
                    '\n' -> lf++
                }
                i++
            }
            return when {
                crlf >= lf && crlf >= cr && crlf > 0 -> CRLF
                cr > lf && cr > 0 -> CR
                else -> LF
            }
        }
    }
}
