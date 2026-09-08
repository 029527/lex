package dev.lex.editor.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.lex.editor.data.DirectoryLister
import dev.lex.editor.data.RecentTreeStore
import dev.lex.editor.data.SettingsStore
import dev.lex.editor.ui.browser.BrowseScreen
import dev.lex.editor.ui.browser.HomeScreen
import dev.lex.editor.ui.editor.EditorScreen
import dev.lex.editor.ui.settings.SettingsScreen

/** Where the user is. Deliberately tiny: Lex has four places to be. */
sealed interface Screen {
    data object Home : Screen
    data class Browse(val treeUri: Uri, val documentId: String?, val title: String) : Screen
    data class Edit(val uri: Uri) : Screen
    data object Settings : Screen
}

private val OPEN_DOCUMENT_TYPES = arrayOf(
    "text/*",
    "application/json",
    "application/xhtml+xml",
    "*/*",
)

@Composable
fun LexApp(settingsStore: SettingsStore, initialUri: Uri? = null) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val recentTrees = remember(appContext) { RecentTreeStore(appContext) }
    val lister = remember(appContext) { DirectoryLister(appContext) }

    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    // Bumped whenever the persisted recents change so the Home list re-reads them.
    var recentsRevision by remember { mutableStateOf(0) }

    fun push(screen: Screen) {
        if (backStack.lastOrNull() != screen) backStack.add(screen)
    }

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    // A document handed to us by another app (ACTION_VIEW / ACTION_EDIT).
    LaunchedEffect(initialUri) {
        val uri = initialUri ?: return@LaunchedEffect
        if (backStack.lastOrNull() != Screen.Edit(uri)) backStack.add(Screen.Edit(uri))
    }

    val openFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            takePersistablePermission(context, treeUri)
            val label = runCatching { lister.displayNameOf(treeUri) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: treeUri.lastPathSegment.orEmpty()
            runCatching { recentTrees.remember(treeUri, label) }
            recentsRevision++
            push(Screen.Browse(treeUri, null, label))
        }
    }

    val openFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            takePersistablePermission(context, uri)
            push(Screen.Edit(uri))
        }
    }

    BackHandler(enabled = backStack.size > 1) { pop() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (val current = backStack.last()) {
            Screen.Home -> {
                val recents = remember(recentsRevision) {
                    runCatching { recentTrees.all() }.getOrDefault(emptyList())
                }
                HomeScreen(
                    recents = recents,
                    onOpenFolder = { openFolder.launch(null) },
                    onOpenFile = { openFile.launch(OPEN_DOCUMENT_TYPES) },
                    onOpenRecent = { recent ->
                        runCatching { recentTrees.remember(recent.treeUri, recent.label) }
                        recentsRevision++
                        push(Screen.Browse(recent.treeUri, null, recent.label))
                    },
                    onForgetRecent = { recent ->
                        runCatching { recentTrees.forget(recent.treeUri) }
                        recentsRevision++
                    },
                    onOpenSettings = { push(Screen.Settings) },
                )
            }

            is Screen.Browse -> BrowseScreen(
                lister = lister,
                treeUri = current.treeUri,
                documentId = current.documentId,
                title = current.title,
                onBack = { pop() },
                onOpenDirectory = { entry ->
                    push(Screen.Browse(current.treeUri, entry.documentId, entry.name))
                },
                onOpenFile = { entry -> push(Screen.Edit(entry.uri)) },
            )

            is Screen.Edit -> EditorScreen(
                uri = current.uri,
                onBack = { pop() },
            )

            Screen.Settings -> SettingsScreen(
                settingsStore = settingsStore,
                onBack = { pop() },
            )
        }
    }
}

/**
 * SAF only lets us persist the flags the picker actually granted, and a read-only pick throws
 * when we ask for write. Try both, then settle for read.
 */
private fun takePersistablePermission(context: android.content.Context, uri: Uri) {
    val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
    val write = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    val resolver = context.contentResolver
    val both = runCatching { resolver.takePersistableUriPermission(uri, read or write) }
    if (both.isFailure) {
        runCatching { resolver.takePersistableUriPermission(uri, read) }
    }
}
