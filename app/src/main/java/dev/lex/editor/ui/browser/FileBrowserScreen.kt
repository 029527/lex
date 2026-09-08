package dev.lex.editor.ui.browser

import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lex.editor.R
import dev.lex.editor.data.DirectoryLister
import dev.lex.editor.data.EncodingDetector
import dev.lex.editor.model.DirEntry
import dev.lex.editor.model.FileType
import dev.lex.editor.model.RecentTree
import dev.lex.editor.ui.localizedContext
import dev.lex.editor.ui.str
import java.util.Locale

/* ---------------------------------------------------------------------- Home */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recents: List<RecentTree>,
    onOpenFolder: () -> Unit,
    onOpenFile: () -> Unit,
    onOpenRecent: (RecentTree) -> Unit,
    onForgetRecent: (RecentTree) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(str(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = str(R.string.home_settings),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = str(R.string.home_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onOpenFolder,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(str(R.string.home_open_folder), style = MaterialTheme.typography.titleSmall)
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOpenFile,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(str(R.string.home_open_file), style = MaterialTheme.typography.titleSmall)
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = str(R.string.home_recent_folders),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))

            if (recents.isEmpty()) {
                Text(
                    text = str(R.string.home_no_recents),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                ) {
                    items(recents, key = { it.treeUri.toString() }) { recent ->
                        RecentRow(
                            recent = recent,
                            onClick = { onOpenRecent(recent) },
                            onForget = { onForgetRecent(recent) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRow(
    recent: RecentTree,
    onClick: () -> Unit,
    onForget: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = recent.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = readablePath(recent.treeUri),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onForget) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = str(R.string.home_forget_folder, recent.label),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/* -------------------------------------------------------------------- Browse */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    lister: DirectoryLister,
    treeUri: Uri,
    documentId: String?,
    title: String,
    onBack: () -> Unit,
    onOpenDirectory: (DirEntry) -> Unit,
    onOpenFile: (DirEntry) -> Unit,
) {
    var entries by remember(treeUri, documentId) { mutableStateOf<List<DirEntry>?>(null) }

    LaunchedEffect(treeUri, documentId) {
        val loaded = runCatching { lister.listChildren(treeUri, documentId) }
            .getOrDefault(emptyList())
        entries = loaded.sortedWith(
            compareByDescending<DirEntry> { it.isDirectory }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title.ifBlank { str(R.string.home_title) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = str(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val current = entries
            when {
                current == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                current.isEmpty() -> Text(
                    text = str(R.string.browse_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    items(current, key = { it.documentId }) { entry ->
                        EntryRow(
                            entry = entry,
                            onClick = {
                                if (entry.isDirectory) onOpenDirectory(entry) else onOpenFile(entry)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: DirEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = iconFor(entry),
                contentDescription = null,
                tint = if (entry.isDirectory) {
                    MaterialTheme.colorScheme.primary
                } else {
                    tintFor(entry.type)
                },
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Folders have nothing to say here; their icon already reports what they are.
            val subtitle = subtitleFor(entry)
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (!entry.isDirectory && entry.type != FileType.PLAIN) {
            Spacer(Modifier.width(8.dp))
            TypeBadge(entry.type)
        }
    }
}

@Composable
private fun TypeBadge(type: FileType) {
    Text(
        // Only the three untranslated format names reach this badge, so uppercasing is safe.
        text = str(type.labelRes).uppercase(Locale.ROOT),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        color = tintFor(type),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun iconFor(entry: DirEntry) = when {
    entry.isDirectory -> Icons.Filled.Folder
    entry.type == FileType.HTML || entry.type == FileType.JSON -> Icons.Filled.Code
    else -> Icons.Filled.Description
}

/** Fixed accents rather than scheme roles: they need to stay distinguishable in both themes. */
private fun tintFor(type: FileType): Color = when (type) {
    FileType.MARKDOWN -> Color(0xFF4F8AC9)
    FileType.HTML -> Color(0xFFD97A4E)
    FileType.JSON -> Color(0xFF5FA45F)
    FileType.PLAIN -> Color(0xFF8B9198)
}

/**
 * Sizes and relative times are formatted through the composition's context, which
 * [dev.lex.editor.ui.LocalizedContent] overrides, so they follow the in-app language
 * rather than the system one.
 */
@Composable
private fun subtitleFor(entry: DirEntry): String {
    if (entry.isDirectory) return ""
    val context = localizedContext()
    val parts = mutableListOf<String>()
    if (entry.sizeBytes > 0L) parts += Formatter.formatShortFileSize(context, entry.sizeBytes)
    if (entry.lastModified > 0L) {
        parts += DateUtils.getRelativeTimeSpanString(
            context,
            entry.lastModified,
            /* withPreposition = */ false,
        ).toString()
    }
    return parts.joinToString("  \u00b7  ")
}

/** SAF tree URIs are unreadable; show the document-id tail, which is usually the real path. */
private fun readablePath(treeUri: Uri): String {
    val decoded = Uri.decode(treeUri.toString())
    val tail = decoded.substringAfterLast("/tree/", decoded)
    return tail.substringAfter(':', tail).ifBlank { decoded }
}
