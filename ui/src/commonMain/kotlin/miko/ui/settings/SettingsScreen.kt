package miko.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.icerock.moko.resources.StringResource
import miko.ui.components.SettingDropdown
import miko.ui.components.SettingSlider
import miko.ui.extensions.ExtensionHeader
import miko.ui.reader.NovelReaderBackgroundColor
import miko.ui.reader.TextAlignment
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Android's SettingsScreen, scoped — appearance + library prefs that the
 * shared UI actually consumes today. Same pref keys as Android.
 */
@Composable
fun SettingsScreen(
    model: SettingsScreenModel,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ExtensionHeader(stringResource(MR.strings.pref_category_appearance))
        ThemeModeRow(
            current = state.themeMode,
            onSelect = model::setThemeMode,
        )

        ExtensionHeader(stringResource(MR.strings.pref_category_library))
        SwitchRow(
            titleRes = MR.strings.action_display_show_library_title,
            checked = state.showLibraryTitle,
            onChange = model::setShowLibraryTitle,
        )

        ExtensionHeader(stringResource(MR.strings.pref_category_reader))
        SettingSlider(
            labelRes = MR.strings.text_size,
            value = state.readerTextSize,
            range = 10f..30f,
            onChange = model::setReaderTextSize,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        SettingSlider(
            labelRes = MR.strings.line_height,
            value = state.readerLineHeight,
            range = 1f..2.5f,
            onChange = model::setReaderLineHeight,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        SettingSlider(
            labelRes = MR.strings.paragraph_spacing,
            value = state.readerParagraphSpacing.toFloat(),
            range = 0f..40f,
            onChange = { model.setReaderParagraphSpacing(it.toInt()) },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        SettingDropdown(
            labelRes = MR.strings.text_alignment,
            current = stringResource(state.readerTextAlignment.titleRes),
            options = TextAlignment.entries.map { it to it.titleRes },
            onSelect = model::setReaderTextAlignment,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        SettingDropdown(
            labelRes = MR.strings.pref_reader_theme,
            current = stringResource(state.readerBackgroundColor.titleRes),
            options = NovelReaderBackgroundColor.entries.map { it to it.titleRes },
            onSelect = model::setReaderBackgroundColor,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // Android's PlayerSettings skip block — same keys/defaults; the
        // countdown timeout only matters with Netflix style enabled.
        ExtensionHeader(stringResource(AYMR.strings.label_player))
        SwitchRow(
            titleRes = AYMR.strings.pref_enable_intro_skip,
            checked = state.enableSkipIntro,
            onChange = model::setEnableSkipIntro,
        )
        SwitchRow(
            titleRes = AYMR.strings.pref_enable_auto_skip_ani_skip,
            checked = state.autoSkipIntro,
            onChange = model::setAutoSkipIntro,
        )
        SwitchRow(
            titleRes = AYMR.strings.pref_enable_netflix_style_aniskip,
            checked = state.netflixStyleSkip,
            onChange = model::setNetflixStyleSkip,
        )
        SettingDropdown(
            labelRes = AYMR.strings.pref_waiting_time_aniskip,
            current = stringResource(MR.strings.seconds_short, state.waitingTimeSkip),
            options = (5..10).map {
                it to when (it) {
                    5 -> AYMR.strings.pref_waiting_time_aniskip_5
                    6 -> AYMR.strings.pref_waiting_time_aniskip_6
                    7 -> AYMR.strings.pref_waiting_time_aniskip_7
                    8 -> AYMR.strings.pref_waiting_time_aniskip_8
                    9 -> AYMR.strings.pref_waiting_time_aniskip_9
                    else -> AYMR.strings.pref_waiting_time_aniskip_10
                }
            },
            onSelect = model::setWaitingTimeSkip,
            modifier = Modifier.padding(horizontal = 16.dp),
            enabled = state.netflixStyleSkip,
        )
        SwitchRow(
            titleRes = AYMR.strings.pref_enable_aniskip,
            checked = state.aniSkipEnabled,
            onChange = model::setAniSkipEnabled,
        )
        Text(
            text = stringResource(AYMR.strings.pref_category_player_aniskip_info),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        SwitchRow(
            titleRes = AYMR.strings.pref_disable_aniskip_chapter,
            checked = state.disableAniSkipOnChapters,
            onChange = model::setDisableAniSkipOnChapters,
        )

        // Android's Settings → Advanced BYOK row (pref_tmdb_api_key) —
        // Discovery facet filters use it when set.
        ExtensionHeader(stringResource(MR.strings.pref_category_advanced))
        EditTextRow(
            titleRes = AYMR.strings.pref_tmdb_api_key,
            subtitleRes = AYMR.strings.pref_tmdb_api_key_summary,
            value = state.tmdbApiKey,
            onChange = model::setTmdbApiKey,
        )
    }
}

@Composable
private fun ThemeModeRow(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = stringResource(MR.strings.pref_theme_mode),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(current.titleRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(stringResource(mode.titleRes)) },
                    onClick = {
                        onSelect(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Row + edit dialog — Android's `EditTextPreference` shape. */
@Composable
private fun EditTextRow(
    titleRes: StringResource,
    subtitleRes: StringResource,
    value: String,
    onChange: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { editing = true }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(subtitleRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (editing) {
        var draft by remember(value) { mutableStateOf(value) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(titleRes)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onChange(draft)
                    editing = false
                }) {
                    Text(stringResource(MR.strings.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) {
                    Text(stringResource(AYMR.strings.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SwitchRow(
    titleRes: StringResource,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
