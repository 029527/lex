package dev.lex.editor.ui.editor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lex.editor.R
import dev.lex.editor.format.PreviewDocument
import dev.lex.editor.model.FileType
import dev.lex.editor.ui.preview.PreviewPane
import dev.lex.editor.ui.str
import kotlinx.coroutines.delay

/**
 * Android hands clipboard data across a Binder transaction, which throws above roughly 1 MB.
 * Stay well underneath and tell the user instead of crashing.
 */
private const val CLIPBOARD_LIMIT_CHARS = 256 * 1024

/** Above this many lines the gutter costs more than it is worth, so it steps aside. */
private const val GUTTER_LINE_LIMIT = 5000

private val EditorFontSize = 13.5.sp
private val EditorLineHeight = 20.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    uri: Uri,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: EditorViewModel = viewModel(
        key = uri.toString(),
        factory = remember(context) { EditorViewModel.factory(context) },
    )

    LaunchedEffect(uri) { viewModel.load(uri) }

    val state = viewModel.uiState
    val value = viewModel.textFieldValue
    val fileType = state.meta?.type
    // The preview follows the palette actually in use, which may be an explicit theme override
    // rather than the system's own light/dark setting.
    val surfaceColor = MaterialTheme.colorScheme.surface
    val darkTheme = remember(surfaceColor) { surfaceColor.luminance() < 0.5f }

    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val focusRequester = remember { FocusRequester() }

    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val lineStarts = remember(value.text) { lineStartsOf(value.text) }

    val messageRes = state.messageRes
    val messageArg = state.messageArg
    val statusMessage: String? = when {
        messageRes == null -> state.messageText
        messageArg != null -> str(messageRes, messageArg)
        else -> str(messageRes)
    }

    LaunchedEffect(statusMessage) {
        val message = statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeStatusMessage()
    }

    // Rebuilding the preview is not free, so wait for a pause in typing.
    var previewSource by remember { mutableStateOf("") }
    LaunchedEffect(value.text, state.showPreview) {
        if (state.showPreview) {
            delay(300)
            previewSource = value.text
        }
    }
    val previewHtml = remember(previewSource, darkTheme, fileType) {
        if (fileType == null) null else PreviewDocument.build(fileType, previewSource, darkTheme)
    }

    val requestBack: () -> Unit = {
        if (state.isDirty) confirmDiscard = true else onBack()
    }

    // Only intercept back while there is something to lose; otherwise the app-level handler pops.
    BackHandler(enabled = state.isDirty) { confirmDiscard = true }

    fun copyAll() {
        val body = value.text
        if (body.length > CLIPBOARD_LIMIT_CHARS) {
            viewModel.showStatus(R.string.editor_copy_too_large)
            return
        }
        val copied = runCatching { clipboard.setText(AnnotatedString(body)) }.isSuccess
        viewModel.showStatus(
            if (copied) R.string.editor_copied else R.string.editor_copy_refused
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = state.meta?.displayName ?: str(R.string.browse_loading),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (state.isDirty) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = str(R.string.action_back),
                        )
                    }
                },
                actions = {
                    FilterChip(
                        selected = state.isEditable,
                        enabled = state.meta != null,
                        onClick = { viewModel.setEditable(!state.isEditable) },
                        label = {
                            Text(
                                text = str(
                                    if (state.isEditable) {
                                        R.string.editor_mode_edit
                                    } else {
                                        R.string.editor_mode_read
                                    }
                                ),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (state.isEditable) Icons.Filled.Edit else Icons.Filled.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )

                    IconButton(
                        onClick = { viewModel.save() },
                        enabled = state.isEditable && state.isDirty,
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = str(R.string.action_save))
                    }

                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = str(R.string.action_more),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(str(R.string.editor_select_all)) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.selectAll()
                                    runCatching { focusRequester.requestFocus() }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(str(R.string.editor_copy_all)) },
                                onClick = {
                                    menuExpanded = false
                                    copyAll()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(str(R.string.editor_format)) },
                                enabled = state.isEditable && fileType?.canFormat == true,
                                onClick = {
                                    menuExpanded = false
                                    viewModel.format()
                                },
                            )
                            if (fileType == FileType.JSON) {
                                DropdownMenuItem(
                                    text = { Text(str(R.string.editor_minify_json)) },
                                    enabled = state.isEditable,
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.minifyJson()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(str(R.string.editor_undo)) },
                                enabled = state.isEditable && viewModel.canUndo,
                                onClick = {
                                    menuExpanded = false
                                    viewModel.undo()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(str(R.string.editor_redo)) },
                                enabled = state.isEditable && viewModel.canRedo,
                                onClick = {
                                    menuExpanded = false
                                    viewModel.redo()
                                },
                            )
                            if (fileType?.canPreview == true) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            str(
                                                if (state.showPreview) {
                                                    R.string.editor_hide_preview
                                                } else {
                                                    R.string.editor_show_preview
                                                }
                                            )
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        previewSource = value.text
                                        viewModel.togglePreview()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                // The item names the action, not the state: it turns wrapping off
                                // when it is on.
                                text = {
                                    Text(
                                        str(
                                            if (viewModel.wordWrap) {
                                                R.string.editor_word_wrap_off
                                            } else {
                                                R.string.editor_word_wrap_on
                                            }
                                        )
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.toggleWordWrap()
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            EditorStatusBar(
                meta = state.meta,
                lineStarts = lineStarts,
                charCount = value.text.length,
                cursorOffset = value.selection.start,
                isEditable = state.isEditable,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val errorRes = state.errorRes
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                errorRes != null -> ErrorState(
                    message = str(errorRes),
                    detail = state.errorDetail,
                    onRetry = { viewModel.retry() },
                    modifier = Modifier.align(Alignment.Center),
                )

                state.showPreview -> PreviewPane(html = previewHtml)

                else -> EditorSurface(
                    value = value,
                    onValueChange = { changed -> viewModel.onTextChange(changed) },
                    readOnly = !state.isEditable,
                    wordWrap = viewModel.wordWrap,
                    lineStarts = lineStarts,
                    focusRequester = focusRequester,
                )
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(str(R.string.editor_discard_title)) },
            text = { Text(str(R.string.editor_discard_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        onBack()
                    }
                ) { Text(str(R.string.editor_discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(str(R.string.editor_discard_cancel))
                }
            },
        )
    }
}

@Composable
private fun ErrorState(
    message: String,
    detail: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (detail != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text(str(R.string.action_retry)) }
    }
}

/**
 * The text surface: a line-number gutter and the field itself, sharing one scroll container so the
 * numbers cannot drift away from their lines.
 *
 * With word wrap off the whole row scrolls horizontally, which also leaves the field's width
 * unbounded — that is what stops long lines from wrapping.
 */
@Composable
private fun EditorSurface(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    readOnly: Boolean,
    wordWrap: Boolean,
    lineStarts: List<Int>,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val textStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = EditorFontSize,
        lineHeight = EditorLineHeight,
        color = colors.onSurface,
    )
    val gutterStyle = textStyle.copy(color = colors.outline)
    val showGutter = lineStarts.size <= GUTTER_LINE_LIMIT
    val gutterWidth = (20 + 9 * lineStarts.size.toString().length).dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (readOnly) colors.surfaceContainerLow else colors.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(verticalScrollState)
                .then(
                    if (wordWrap) Modifier else Modifier.horizontalScroll(horizontalScrollState)
                )
                // Room to keep the last line clear of the status bar.
                .padding(top = 10.dp, bottom = 120.dp),
        ) {
            if (showGutter) {
                LineNumberGutter(
                    lineStarts = lineStarts,
                    layoutResult = layoutResult,
                    text = value.text,
                    width = gutterWidth,
                    style = gutterStyle,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                readOnly = readOnly,
                textStyle = textStyle,
                cursorBrush = SolidColor(colors.primary),
                onTextLayout = { layoutResult = it },
                modifier = (if (wordWrap) Modifier.weight(1f) else Modifier)
                    .focusRequester(focusRequester)
                    .padding(start = if (showGutter) 10.dp else 16.dp, end = 16.dp),
            )
        }
    }
}

/**
 * Numbers are laid out as one box per logical line, each box exactly as tall as the visual lines
 * that logical line occupies. That keeps the gutter honest when word wrap splits a line in two.
 */
@Composable
private fun LineNumberGutter(
    lineStarts: List<Int>,
    layoutResult: TextLayoutResult?,
    text: String,
    width: Dp,
    style: TextStyle,
) {
    val density = LocalDensity.current
    val fallbackHeight = with(density) { EditorLineHeight.toDp() }
    // A stale layout would misalign every number below the edit, so only trust a matching one.
    val layout = layoutResult?.takeIf { it.layoutInput.text.text == text }

    Column(modifier = Modifier.width(width)) {
        for (index in lineStarts.indices) {
            val height = if (layout == null) {
                fallbackHeight
            } else {
                val start = lineStarts[index].coerceIn(0, text.length)
                val end = (if (index + 1 < lineStarts.size) lineStarts[index + 1] - 1 else text.length)
                    .coerceIn(start, text.length)
                val firstVisual = layout.getLineForOffset(start)
                val lastVisual = layout.getLineForOffset(end)
                with(density) {
                    (layout.getLineBottom(lastVisual) - layout.getLineTop(firstVisual)).toDp()
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height.coerceAtLeast(0.dp)),
                contentAlignment = Alignment.TopEnd,
            ) {
                Text(text = (index + 1).toString(), style = style, maxLines = 1)
            }
        }
    }
}
