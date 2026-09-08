package dev.lex.editor.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How Lex picks between the light and dark palettes. */
enum class ThemePreference { Automatic, Light, Dark }

@Immutable
data class AppSettings(
    val theme: ThemePreference = ThemePreference.Automatic,
    /** BCP-47 tag, or null to follow the system / per-app Android language setting. */
    val languageTag: String? = null,
)

/**
 * The two preferences Lex persists, held in memory as a [StateFlow] so Compose can read them
 * synchronously and recompose on change.
 *
 * SharedPreferences rather than DataStore: two scalar values do not justify pulling in another
 * dependency, and the reads are small enough to do on the main thread at construction.
 */
class SettingsStore(context: Context) {

    // The application context: this store outlives any single Activity.
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())

    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setTheme(theme: ThemePreference) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
        _settings.value = _settings.value.copy(theme = theme)
    }

    /** A null or blank [tag] means "follow the system", stored as the absence of the key. */
    fun setLanguageTag(tag: String?) {
        val normalized = tag?.takeIf { it.isNotBlank() }
        prefs.edit().apply {
            if (normalized == null) remove(KEY_LANGUAGE) else putString(KEY_LANGUAGE, normalized)
        }.apply()
        _settings.value = _settings.value.copy(languageTag = normalized)
    }

    private fun read(): AppSettings = AppSettings(
        theme = themeFrom(prefs.getString(KEY_THEME, null)),
        languageTag = prefs.getString(KEY_LANGUAGE, null)?.takeIf { it.isNotBlank() },
    )

    companion object {
        /** BCP-47 tags Lex ships translations for, in menu order. */
        val SupportedLanguageTags: List<String> = listOf("en", "zh-Hans")

        private const val PREFS_NAME = "lex_settings"
        private const val KEY_THEME = "theme"
        private const val KEY_LANGUAGE = "language_tag"

        /** A missing value, or one written by a build that knew a name this one does not. */
        private fun themeFrom(name: String?): ThemePreference =
            ThemePreference.entries.firstOrNull { it.name == name } ?: ThemePreference.Automatic
    }
}
