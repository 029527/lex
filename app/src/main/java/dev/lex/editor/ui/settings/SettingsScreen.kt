package dev.lex.editor.ui.settings

import androidx.annotation.StringRes
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.lex.editor.R
import androidx.core.net.toUri
import dev.lex.editor.data.SettingsStore
import dev.lex.editor.data.ThemePreference
import dev.lex.editor.data.UpdateChecker
import dev.lex.editor.data.UpdateStatus
import dev.lex.editor.ui.str
import kotlinx.coroutines.launch

/** The theme choices, in the order they are offered. */
private val ThemeChoices: List<Pair<ThemePreference, Int>> = listOf(
    ThemePreference.Automatic to R.string.theme_system,
    ThemePreference.Light to R.string.theme_light,
    ThemePreference.Dark to R.string.theme_dark,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit,
) {
    val settings by settingsStore.settings.collectAsState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateChecker = remember { UpdateChecker(context) }
    var updateStatus by remember { mutableStateOf<UpdateStatus>(UpdateStatus.Idle) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(str(R.string.settings_title)) },
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
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(str(R.string.settings_appearance))
            Column(Modifier.selectableGroup()) {
                for ((preference, labelRes) in ThemeChoices) {
                    ChoiceRow(
                        label = str(labelRes),
                        selected = settings.theme == preference,
                        onSelect = { settingsStore.setTheme(preference) },
                    )
                }
            }

            SectionHeader(str(R.string.settings_language))
            Column(Modifier.selectableGroup()) {
                ChoiceRow(
                    label = str(R.string.language_system),
                    selected = settings.languageTag == null,
                    onSelect = { settingsStore.setLanguageTag(null) },
                )
                for (tag in SettingsStore.SupportedLanguageTags) {
                    ChoiceRow(
                        // Each language names itself, so it stays readable whatever is selected.
                        label = str(languageLabelRes(tag)),
                        selected = settings.languageTag == tag,
                        onSelect = { settingsStore.setLanguageTag(tag) },
                    )
                }
            }

            SectionHeader(str(R.string.settings_updates))
            ListItem(
                headlineContent = { Text(str(R.string.update_check)) },
                supportingContent = updateStatus.supportingText(),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(enabled = updateStatus != UpdateStatus.Checking) {
                    updateStatus = UpdateStatus.Checking
                    scope.launch { updateStatus = updateChecker.check() }
                },
            )
            ListItem(
                headlineContent = { Text(str(R.string.update_open_releases)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                // Handing the URL to the browser keeps REQUEST_INSTALL_PACKAGES out of the
                // manifest: the download and the install both happen outside Lex.
                modifier = Modifier.clickable {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, UpdateChecker.RELEASES_URL.toUri())
                        )
                    }
                },
            )
            Text(
                text = str(R.string.update_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            )

            SectionHeader(str(R.string.settings_about))
            ListItem(
                headlineContent = { Text(str(R.string.settings_version)) },
                supportingContent = { Text(versionName()) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            Text(
                text = str(R.string.settings_local_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            )
        }
    }
}

/** A group label, the way Android's own Settings introduces a section. */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

/**
 * One option in a single-choice group. The whole row is the target and carries the radio
 * semantics; the button itself is decorative, hence `onClick = null`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = onSelect,
        ),
    )
}

@StringRes
private fun languageLabelRes(tag: String): Int = when (tag) {
    "zh-Hans" -> R.string.language_zh_hans
    else -> R.string.language_en
}

/** BuildConfig is not generated for this module, so ask the package manager. */
@Composable
private fun versionName(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
}

/** The status line under Check for Updates, or nothing at all before the first check. */
@Composable
private fun UpdateStatus.supportingText(): (@Composable () -> Unit)? = when (this) {
    UpdateStatus.Idle -> null
    UpdateStatus.Checking -> ({ Text(str(R.string.update_checking)) })
    UpdateStatus.UpToDate -> ({ Text(str(R.string.update_up_to_date)) })
    is UpdateStatus.Available -> ({
        Text(
            text = str(R.string.update_available, versionName),
            color = MaterialTheme.colorScheme.primary,
        )
    })
    UpdateStatus.Failed -> ({
        Text(
            text = str(R.string.update_failed),
            color = MaterialTheme.colorScheme.error,
        )
    })
}
