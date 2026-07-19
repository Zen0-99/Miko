package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.TextAlignment
import eu.kanade.tachiyomi.ui.reader.novel.NovelReadingMode
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsNovelReaderScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = AYMR.strings.pref_category_novel_reader

    @ReadOnlyComposable
    @Composable
    override fun getSubtitleRes() = AYMR.strings.pref_novel_reader_summary

    @Composable
    override fun getPreferences(): List<Preference> {
        val pref = remember { Injekt.get<NovelReaderPreferences>() }

        return listOf(
            Preference.PreferenceGroup(
                title = "Text",
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SliderPreference(
                        value = pref.textSize().get().toInt(),
                        title = "Text size",
                        valueRange = 10..32,
                        onValueChanged = { newValue ->
                            pref.textSize().set(newValue.toFloat())
                            true
                        },
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = (pref.lineHeight().get() * 10).toInt(),
                        title = "Line height",
                        valueRange = 10..30,
                        onValueChanged = { newValue ->
                            pref.lineHeight().set(newValue / 10f)
                            true
                        },
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = pref.paragraphSpacing().get(),
                        title = "Paragraph spacing",
                        valueRange = 0..48,
                        onValueChanged = { newValue ->
                            pref.paragraphSpacing().set(newValue)
                            true
                        },
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = pref.textAlignment(),
                        entries = TextAlignment.entries.associate { it to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }.toImmutableMap(),
                        title = "Text alignment",
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = "Reading",
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = pref.readingMode(),
                        entries = NovelReadingMode.entries.associate { it to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }.toImmutableMap(),
                        title = "Reading mode",
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = pref.keepScreenOn(),
                        title = "Keep screen on",
                        subtitle = "Prevents screen from turning off while reading",
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = pref.showReadingProgress(),
                        title = "Show reading progress",
                        subtitle = "Display progress bar at bottom",
                    ),
                ),
            ),
        )
    }
}
