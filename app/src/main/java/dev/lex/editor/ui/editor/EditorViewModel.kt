package dev.lex.editor.ui.editor

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.lex.editor.R
import dev.lex.editor.data.DocumentStore
import dev.lex.editor.format.Formatters
import dev.lex.editor.format.JsonFormatter
import dev.lex.editor.model.DocumentMeta
import dev.lex.editor.model.FileType
import dev.lex.editor.model.FormatResult
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Messages are carried as resource ids, never as text: the view model has no context, and the
 * in-app language override is only known to the composables that resolve them.
 */
data class EditorUiState(
    val isLoading: Boolean = true,
    val meta: DocumentMeta? = null,
    @param:StringRes val errorRes: Int? = null,
    /**
     * The data layer's own explanation of a failed open, shown under [errorRes].
     * It names concrete facts the localised headline cannot -- the file's size
     * against the limit, or that the folder grant was revoked -- so it is passed
     * through verbatim rather than discarded.
     */
    val errorDetail: String? = null,
    val isEditable: Boolean = false,
    val isDirty: Boolean = false,
    val showPreview: Boolean = false,
    @param:StringRes val messageRes: Int? = null,
    /** Substituted into [messageRes] when it takes a `%1$s`. */
    val messageArg: String? = null,
    /** A parser's own complaint about the document, passed through verbatim. */
    val messageText: String? = null,
)

/** One undoable position in the document's history. */
private data class TextSnapshot(val text: String, val selection: TextRange)

private const val UNDO_LIMIT = 50

/** Keystrokes closer together than this coalesce into a single undo step. */
private const val UNDO_COALESCE_MS = 400L

class EditorViewModel(private val store: DocumentStore) : ViewModel() {

    var uiState by mutableStateOf(EditorUiState())
        private set

    /**
     * The document body. A [TextFieldValue] rather than a plain String so selection can be driven
     * from the UI (select all, cursor restore after formatting).
     */
    var textFieldValue by mutableStateOf(TextFieldValue(""))
        private set

    var wordWrap by mutableStateOf(true)
        private set

    var canUndo by mutableStateOf(false)
        private set

    var canRedo by mutableStateOf(false)
        private set

    private val undoStack = ArrayDeque<TextSnapshot>()
    private val redoStack = ArrayDeque<TextSnapshot>()
    private var lastSnapshotAt = 0L
    private var loadedUri: Uri? = null

    fun load(uri: Uri) {
        if (loadedUri == uri) return
        loadedUri = uri
        uiState = EditorUiState(isLoading = true)
        viewModelScope.launch {
            store.load(uri).fold(
                onSuccess = { document ->
                    textFieldValue = TextFieldValue(document.text)
                    undoStack.clear()
                    redoStack.clear()
                    canUndo = false
                    canRedo = false
                    // Prose wraps; structured formats read better on their own long lines.
                    wordWrap = document.meta.type == FileType.MARKDOWN ||
                        document.meta.type == FileType.PLAIN
                    uiState = EditorUiState(isLoading = false, meta = document.meta)
                },
                onFailure = {
                    uiState = EditorUiState(
                        isLoading = false,
                        errorRes = R.string.editor_open_failed,
                        errorDetail = it.message?.takeIf { detail -> detail.isNotBlank() },
                    )
                },
            )
        }
    }

    fun retry() {
        val uri = loadedUri ?: return
        loadedUri = null
        load(uri)
    }

    /** Switching to read-only never touches the buffer, so unsaved edits survive the toggle. */
    fun setEditable(editable: Boolean) {
        if (uiState.isEditable == editable) return
        uiState = uiState.copy(isEditable = editable)
    }

    fun onTextChange(next: TextFieldValue) {
        val previous = textFieldValue
        if (previous.text == next.text) {
            // Caret or selection moved only.
            textFieldValue = next
            return
        }
        val now = System.currentTimeMillis()
        val substantial = abs(next.text.length - previous.text.length) > 1 || previous.selection.length > 0
        if (substantial || now - lastSnapshotAt > UNDO_COALESCE_MS) {
            pushUndo(TextSnapshot(previous.text, previous.selection))
            lastSnapshotAt = now
        }
        textFieldValue = next
        redoStack.clear()
        canRedo = false
        if (!uiState.isDirty) uiState = uiState.copy(isDirty = true)
    }

    fun selectAll() {
        textFieldValue = textFieldValue.copy(
            selection = TextRange(0, textFieldValue.text.length)
        )
    }

    fun undo() {
        val snapshot = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(TextSnapshot(textFieldValue.text, textFieldValue.selection))
        if (redoStack.size > UNDO_LIMIT) redoStack.removeFirst()
        applySnapshot(snapshot)
        canUndo = undoStack.isNotEmpty()
        canRedo = true
    }

    fun redo() {
        val snapshot = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(TextSnapshot(textFieldValue.text, textFieldValue.selection))
        if (undoStack.size > UNDO_LIMIT) undoStack.removeFirst()
        applySnapshot(snapshot)
        canUndo = true
        canRedo = redoStack.isNotEmpty()
    }

    fun save() {
        val meta = uiState.meta ?: return
        val body = textFieldValue.text
        viewModelScope.launch {
            store.save(meta, body).fold(
                onSuccess = { saved ->
                    uiState = uiState.copy(
                        meta = saved,
                        isDirty = false,
                        messageRes = R.string.editor_saved,
                        messageArg = null,
                        messageText = null,
                    )
                },
                onFailure = { error ->
                    val detail = error.message?.takeIf { it.isNotBlank() }
                    uiState = uiState.copy(
                        messageRes = if (detail != null) {
                            R.string.editor_save_failed_detail
                        } else {
                            R.string.editor_save_failed
                        },
                        messageArg = detail,
                        messageText = null,
                    )
                },
            )
        }
    }

    fun format() {
        val type = uiState.meta?.type ?: return
        applyFormat(
            result = Formatters.format(type, textFieldValue.text),
            successRes = R.string.editor_formatted,
            unchangedRes = R.string.editor_nothing_to_change,
        )
    }

    fun minifyJson() {
        if (uiState.meta?.type != FileType.JSON) return
        applyFormat(
            result = JsonFormatter.minify(textFieldValue.text),
            successRes = R.string.editor_minified,
            unchangedRes = R.string.editor_already_minified,
        )
    }

    fun togglePreview() {
        uiState = uiState.copy(showPreview = !uiState.showPreview)
    }

    fun toggleWordWrap() {
        wordWrap = !wordWrap
    }

    fun consumeStatusMessage() {
        if (uiState.messageRes != null || uiState.messageText != null) {
            uiState = uiState.copy(messageRes = null, messageArg = null, messageText = null)
        }
    }

    fun showStatus(@StringRes messageRes: Int) {
        uiState = uiState.copy(messageRes = messageRes, messageArg = null, messageText = null)
    }

    private fun applyFormat(
        result: FormatResult,
        @StringRes successRes: Int,
        @StringRes unchangedRes: Int,
    ) {
        when (result) {
            is FormatResult.Success -> {
                if (result.text == textFieldValue.text) {
                    uiState = uiState.copy(
                        messageRes = unchangedRes,
                        messageArg = null,
                        messageText = null,
                    )
                    return
                }
                pushUndo(TextSnapshot(textFieldValue.text, textFieldValue.selection))
                lastSnapshotAt = System.currentTimeMillis()
                val caret = textFieldValue.selection.start.coerceIn(0, result.text.length)
                textFieldValue = TextFieldValue(result.text, TextRange(caret))
                redoStack.clear()
                canRedo = false
                uiState = uiState.copy(
                    isDirty = true,
                    messageRes = successRes,
                    messageArg = null,
                    messageText = null,
                )
            }

            is FormatResult.Failure -> uiState = uiState.copy(
                messageRes = null,
                messageArg = null,
                messageText = result.message,
            )

            FormatResult.Unsupported -> uiState = uiState.copy(
                messageRes = R.string.editor_no_formatter,
                messageArg = null,
                messageText = null,
            )
        }
    }

    private fun pushUndo(snapshot: TextSnapshot) {
        undoStack.addLast(snapshot)
        if (undoStack.size > UNDO_LIMIT) undoStack.removeFirst()
        canUndo = true
    }

    private fun applySnapshot(snapshot: TextSnapshot) {
        val length = snapshot.text.length
        val selection = TextRange(
            snapshot.selection.start.coerceIn(0, length),
            snapshot.selection.end.coerceIn(0, length),
        )
        textFieldValue = TextFieldValue(snapshot.text, selection)
        lastSnapshotAt = System.currentTimeMillis()
        if (!uiState.isDirty) uiState = uiState.copy(isDirty = true)
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    EditorViewModel(DocumentStore(appContext)) as T
            }
        }
    }
}
