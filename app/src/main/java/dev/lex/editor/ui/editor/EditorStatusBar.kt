package dev.lex.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lex.editor.R
import dev.lex.editor.model.DocumentMeta
import dev.lex.editor.ui.plural
import dev.lex.editor.ui.str

/**
 * The slim bar under the editor. Everything here is diagnostic, so it stays small, subdued and
 * horizontally scrollable rather than wrapping or truncating on narrow phones.
 */
@Composable
fun EditorStatusBar(
    meta: DocumentMeta?,
    lineStarts: List<Int>,
    charCount: Int,
    cursorOffset: Int,
    isEditable: Boolean,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val lineIndex = lineIndexFor(lineStarts, cursorOffset)
    val column = cursorOffset - (lineStarts.getOrNull(lineIndex) ?: 0) + 1

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(32.dp)
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isEditable) {
                    ReadOnlyChip()
                    Separator()
                }
                if (meta != null) {
                    StatusText(str(meta.type.labelRes))
                    Separator()
                    // Charset name, BOM marker and line terminator are technical identifiers.
                    StatusText(meta.charsetName + if (meta.hasBom) " " + str(R.string.status_bom) else "")
                    Separator()
                    StatusText(meta.lineEnding.label)
                    Separator()
                }
                StatusText(plural(R.plurals.status_lines, lineStarts.size))
                Separator()
                StatusText(plural(R.plurals.status_characters, charCount))
                Separator()
                StatusText(str(R.string.status_line_column, lineIndex + 1, column))
            }
        }
    }
}

@Composable
private fun StatusText(value: String) {
    Text(
        text = value,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

@Composable
private fun Separator() {
    Spacer(Modifier.width(10.dp))
    Text(
        text = "·",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.outline,
    )
    Spacer(Modifier.width(10.dp))
}

@Composable
private fun ReadOnlyChip() {
    Text(
        text = str(R.string.editor_read_only),
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Index of the line containing [offset], given the start offset of every line. */
internal fun lineIndexFor(lineStarts: List<Int>, offset: Int): Int {
    if (lineStarts.isEmpty()) return 0
    val found = lineStarts.binarySearch(offset)
    return if (found >= 0) found else (-found - 2).coerceAtLeast(0)
}

/** Start offsets of every line in [text]; always at least one entry. */
internal fun lineStartsOf(text: String): List<Int> {
    val starts = ArrayList<Int>(64)
    starts.add(0)
    var index = text.indexOf('\n')
    while (index >= 0) {
        starts.add(index + 1)
        index = text.indexOf('\n', index + 1)
    }
    return starts
}
