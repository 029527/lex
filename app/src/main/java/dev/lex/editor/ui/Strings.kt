package dev.lex.editor.ui

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Context used to resolve strings and locale-sensitive formatting, or null to follow
 * the system.
 *
 * This is deliberately a separate local rather than an override of [LocalContext].
 * `createConfigurationContext` does not wrap the Activity -- it builds a fresh context
 * whose base chain never reaches one -- and several androidx locals, including
 * `LocalActivityResultRegistryOwner`, recover their owner by walking up that chain.
 * Replacing [LocalContext] therefore made `rememberLauncherForActivityResult` throw
 * "No ActivityResultRegistryOwner was provided" the moment the language changed.
 */
private val LocalLocalizedContext = staticCompositionLocalOf<Context?> { null }

/**
 * Makes [languageTag] the language for everything inside [content].
 *
 * A null or blank tag follows the system, which also leaves the Android 13+ per-app
 * language setting in charge. Both branches go through the same
 * [CompositionLocalProvider] call so that switching languages does not move [content]
 * to a different call site, which would discard the whole subtree's state -- including
 * the navigation back stack -- instead of just recomposing it.
 */
@Composable
fun LocalizedContent(languageTag: String?, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val configuration = LocalConfiguration.current

    val localized: Context? = remember(languageTag, configuration) {
        val tag = languageTag?.takeIf { it.isNotBlank() } ?: return@remember null
        val locale = Locale.forLanguageTag(tag)
        val overridden = Configuration(configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        base.createConfigurationContext(overridden)
    }

    CompositionLocalProvider(LocalLocalizedContext provides localized, content = content)
}

/**
 * The context to read user-visible text and locale-sensitive formats from: the in-app
 * language override when there is one, otherwise the ordinary context.
 */
@Composable
@ReadOnlyComposable
fun localizedContext(): Context = LocalLocalizedContext.current ?: LocalContext.current

@Composable
@ReadOnlyComposable
fun str(@StringRes id: Int): String = localizedContext().getString(id)

@Composable
@ReadOnlyComposable
fun str(@StringRes id: Int, vararg formatArgs: Any): String =
    localizedContext().getString(id, *formatArgs)

/** Quantity-aware lookup. Chinese resolves to `other`, which is correct for it. */
@Composable
@ReadOnlyComposable
fun plural(@PluralsRes id: Int, count: Int): String =
    localizedContext().resources.getQuantityString(id, count, count)
