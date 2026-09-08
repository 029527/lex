package dev.lex.editor.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * A calm blue-grey developer-tool palette, used verbatim on Android 11 and below and as the
 * fallback whenever dynamic colour is unavailable.
 */
private val LexLightColors = lightColorScheme(
    primary = Color(0xFF33607F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCEE5FF),
    onPrimaryContainer = Color(0xFF001D33),
    inversePrimary = Color(0xFF9BCBF0),
    secondary = Color(0xFF51606F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E4F6),
    onSecondaryContainer = Color(0xFF0E1D2A),
    tertiary = Color(0xFF5F5B7D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE5DEFF),
    onTertiaryContainer = Color(0xFF1B1836),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBFCFE),
    onBackground = Color(0xFF191C1E),
    surface = Color(0xFFFBFCFE),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41474D),
    surfaceTint = Color(0xFF33607F),
    inverseSurface = Color(0xFF2E3133),
    inverseOnSurface = Color(0xFFF0F1F3),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFBFCFE),
    surfaceDim = Color(0xFFDBDCDF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F6F9),
    surfaceContainer = Color(0xFFEFF1F3),
    surfaceContainerHigh = Color(0xFFE9EBEE),
    surfaceContainerHighest = Color(0xFFE3E5E8),
)

private val LexDarkColors = darkColorScheme(
    primary = Color(0xFF9BCBF0),
    onPrimary = Color(0xFF003353),
    primaryContainer = Color(0xFF134A6A),
    onPrimaryContainer = Color(0xFFCEE5FF),
    inversePrimary = Color(0xFF33607F),
    secondary = Color(0xFFB9C8DA),
    onSecondary = Color(0xFF243240),
    secondaryContainer = Color(0xFF3A4857),
    onSecondaryContainer = Color(0xFFD5E4F6),
    tertiary = Color(0xFFC8C2EA),
    onTertiary = Color(0xFF302D4C),
    tertiaryContainer = Color(0xFF474364),
    onTertiaryContainer = Color(0xFFE5DEFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111416),
    onBackground = Color(0xFFE2E2E5),
    surface = Color(0xFF111416),
    onSurface = Color(0xFFE2E2E5),
    surfaceVariant = Color(0xFF41474D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    surfaceTint = Color(0xFF9BCBF0),
    inverseSurface = Color(0xFFE2E2E5),
    inverseOnSurface = Color(0xFF2E3133),
    outline = Color(0xFF8B9198),
    outlineVariant = Color(0xFF41474D),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF37393C),
    surfaceDim = Color(0xFF111416),
    surfaceContainerLowest = Color(0xFF0C0E11),
    surfaceContainerLow = Color(0xFF191C1E),
    surfaceContainer = Color(0xFF1D2022),
    surfaceContainerHigh = Color(0xFF272A2D),
    surfaceContainerHighest = Color(0xFF323538),
)

@Composable
fun LexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> LexDarkColors
        else -> LexLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
