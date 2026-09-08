package dev.lex.editor.model

/** Outcome of a formatting pass. Formatting must never silently damage a document. */
sealed interface FormatResult {
    /** [text] is the reformatted document, normalised to LF. */
    data class Success(val text: String) : FormatResult

    /** The document could not be parsed; [message] is safe to show to the user. */
    data class Failure(val message: String) : FormatResult

    /** Lex has no formatter for this file type. */
    data object Unsupported : FormatResult
}
