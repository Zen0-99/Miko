package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.NovelTtsHighlightMode
import eu.kanade.tachiyomi.ui.reader.novel.TextAlignment
import eu.kanade.tachiyomi.ui.reader.novel.NovelReadingMode
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.roundToInt

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
            getTtsGroup(pref),
        )
    }

    @Composable
    private fun getTtsGroup(prefs: NovelReaderPreferences): Preference.PreferenceGroup {
        val ttsSpeechRatePref = prefs.ttsSpeechRate()
        val ttsSpeechRate by ttsSpeechRatePref.collectAsState()
        val ttsPitchPref = prefs.ttsPitch()
        val ttsPitch by ttsPitchPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.novel_reader_tts_section),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.ttsEnabled(),
                    title = stringResource(AYMR.strings.novel_reader_tts_enabled),
                    subtitle = stringResource(AYMR.strings.novel_reader_tts_enabled_summary),
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = (ttsSpeechRate * 100).roundToInt(),
                    title = stringResource(AYMR.strings.novel_reader_tts_speech_rate),
                    subtitle = formatTtsPercentage(ttsSpeechRate),
                    valueRange = 50..200,
                    onValueChanged = {
                        ttsSpeechRatePref.set(it / 100f)
                        true
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = (ttsPitch * 100).roundToInt(),
                    title = stringResource(AYMR.strings.novel_reader_tts_pitch),
                    subtitle = formatTtsPercentage(ttsPitch),
                    valueRange = 50..200,
                    onValueChanged = {
                        ttsPitchPref.set(it / 100f)
                        true
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = prefs.ttsHighlightMode(),
                    entries = NovelTtsHighlightMode.entries
                        .associateWith { getTtsHighlightModeLabel(it) }
                        .toImmutableMap(),
                    title = stringResource(AYMR.strings.novel_reader_tts_highlight_mode),
                    subtitle = stringResource(AYMR.strings.novel_reader_tts_highlight_mode_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.ttsWordHighlightEnabled(),
                    title = stringResource(AYMR.strings.novel_reader_tts_word_highlight_enabled),
                    subtitle = stringResource(AYMR.strings.novel_reader_tts_word_highlight_enabled_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.ttsAutoAdvanceChapter(),
                    title = stringResource(AYMR.strings.novel_reader_tts_auto_advance_chapter),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.ttsFollowAlong(),
                    title = stringResource(AYMR.strings.novel_reader_tts_follow_along),
                    subtitle = stringResource(AYMR.strings.novel_reader_tts_follow_along_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.ttsPauseOnManualNavigation(),
                    title = stringResource(AYMR.strings.novel_reader_tts_pause_on_manual_navigation),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.ttsKeepScreenOnDuringPlayback(),
                    title = stringResource(AYMR.strings.novel_reader_tts_keep_screen_on_during_playback),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.ttsPreferTranslatedText(),
                    title = stringResource(AYMR.strings.novel_reader_tts_prefer_translated_text),
                    subtitle = stringResource(AYMR.strings.novel_reader_tts_prefer_translated_text_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.ttsReadChapterTitle(),
                    title = stringResource(AYMR.strings.novel_reader_tts_read_chapter_title),
                ),
            ),
        )
    }

    @Composable
    private fun getTtsHighlightModeLabel(mode: NovelTtsHighlightMode): String {
        return when (mode) {
            NovelTtsHighlightMode.AUTO -> stringResource(AYMR.strings.novel_reader_tts_highlight_mode_auto)
            NovelTtsHighlightMode.EXACT -> stringResource(AYMR.strings.novel_reader_tts_highlight_mode_exact)
            NovelTtsHighlightMode.ESTIMATED -> stringResource(AYMR.strings.novel_reader_tts_highlight_mode_estimated)
            NovelTtsHighlightMode.OFF -> stringResource(AYMR.strings.novel_reader_tts_highlight_mode_off)
        }
    }

    private fun formatTtsPercentage(value: Float): String {
        return "${(value * 100).roundToInt()}%"
    }
}
