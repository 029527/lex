package dev.lex.editor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.lex.editor.data.SettingsStore
import dev.lex.editor.data.ThemePreference
import dev.lex.editor.ui.LexApp
import dev.lex.editor.ui.LocalizedContent
import dev.lex.editor.ui.theme.LexTheme

class MainActivity : ComponentActivity() {

    /** The document another app asked us to open, if any. Kept as state so onNewIntent lands in the UI. */
    private var incomingUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingUri = documentUriOf(intent)

        setContent {
            val context = LocalContext.current
            val store = remember { SettingsStore(context) }
            val settings by store.settings.collectAsState()

            val darkTheme = when (settings.theme) {
                ThemePreference.Automatic -> isSystemInDarkTheme()
                ThemePreference.Light -> false
                ThemePreference.Dark -> true
            }

            // enableEdgeToEdge() picks system-bar icon colours from the system theme, which is the
            // wrong answer whenever the user has overridden it here.
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }

            // Outside LexTheme so that every str() below it resolves in the chosen language.
            LocalizedContent(settings.languageTag) {
                LexTheme(darkTheme = darkTheme) {
                    LexApp(settingsStore = store, initialUri = incomingUri)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        documentUriOf(intent)?.let { incomingUri = it }
    }

    private fun documentUriOf(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> intent.data
            else -> null
        }
    }
}
