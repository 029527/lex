package dev.lex.editor.ui.preview

import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import dev.lex.editor.R
import dev.lex.editor.ui.str

/** Mutable, non-observable holder: the WebView must not reload on every recomposition. */
private class LoadedHtml {
    var value: String? = null
}

/**
 * Renders pre-built HTML for the current document.
 *
 * The document comes from the user's own storage and is therefore untrusted: scripting, file
 * access and content access are all off. Nothing here ever touches the network.
 */
@Composable
fun PreviewPane(
    html: String?,
    modifier: Modifier = Modifier,
) {
    if (html == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = str(R.string.editor_preview_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val loaded = remember { LoadedHtml() }
    val backgroundArgb = MaterialTheme.colorScheme.surface.toArgb()

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.loadsImagesAutomatically = true
                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = false
                setBackgroundColor(AndroidColor.TRANSPARENT)
            }
        },
        update = { webView ->
            webView.setBackgroundColor(backgroundArgb)
            if (loaded.value != html) {
                loaded.value = html
                webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        },
    )
}
