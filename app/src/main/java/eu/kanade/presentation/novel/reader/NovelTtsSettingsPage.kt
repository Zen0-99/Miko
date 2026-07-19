package eu.kanade.presentation.novel.reader

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import eu.kanade.tachiyomi.ui.reader.novel.NovelTtsPreferences
import tachiyomi.presentation.core.util.collectAsState

/**
 * TTS settings page shown as a tab in the reader settings bottom sheet.
 *
 * Provides controls for:
 * - Engine selection (Android TTS / Neural TTS)
 * - Speech rate slider
 * - Pitch slider
 * - Voice selection dropdown
 * - Auto-continue toggle
 * - Background playback toggle
 */
@Composable
fun ColumnScope.NovelTtsSettingsPage(
    preferences: NovelTtsPreferences,
    accentColor: Color? = null,
    availableVoices: List<eu.kanade.tachiyomi.ui.reader.novel.tts.TtsVoice> = emptyList(),
    onRefreshVoices: () -> Unit = {},
) {
    // ---- Engine section ----
    SectionHeader("Text-to-Speech", accentColor)

    val engineType by preferences.engineType().collectAsState()
    val engineLabels = listOf("Android TTS (system)", "Neural TTS (sherpa-onnx)")
    val engineValues = listOf("android", "neural")
    SettingsDropdown(
        label = "TTS engine",
        selectedLabel = engineLabels[engineValues.indexOf(engineType)],
        options = engineLabels,
    ) { index ->
        preferences.engineType().set(engineValues[index])
    }

    // ---- Speech settings ----
    SubHeader("Speech", accentColor)

    val speechRate by preferences.speechRate().collectAsState()
    FloatSliderItem(
        label = "Speech rate",
        value = speechRate,
        valueRange = 0.5f..4.0f,
        valueText = String.format("%.1fx", speechRate),
        steps = 6,
        accentColor = accentColor,
    ) { rate ->
        preferences.speechRate().set(rate)
    }

    val pitch by preferences.pitch().collectAsState()
    FloatSliderItem(
        label = "Pitch",
        value = pitch,
        valueRange = 0.5f..2.0f,
        valueText = String.format("%.1f", pitch),
        steps = 5,
        accentColor = accentColor,
    ) { p ->
        preferences.pitch().set(p)
    }

    // ---- Voice selection ----
    SubHeader("Voice", accentColor)

    if (availableVoices.isNotEmpty()) {
        val voiceName by preferences.voiceName().collectAsState()
        val voiceLabels = listOf("System default") + availableVoices.map { it.displayName }
        val voiceValues = listOf("") + availableVoices.map { it.name }
        val selectedIndex = voiceValues.indexOf(voiceName).coerceAtLeast(0)
        SettingsDropdown(
            label = "Voice",
            selectedLabel = voiceLabels[selectedIndex],
            options = voiceLabels,
        ) { index ->
            preferences.voiceName().set(voiceValues[index])
        }
    } else {
        SettingsDropdown(
            label = "Voice",
            selectedLabel = "Not available",
            options = listOf("Not available"),
        ) { }
    }

    // ---- Playback behavior ----
    SubHeader("Playback", accentColor)

    CheckboxItem(
        label = "Auto-continue to next paragraph",
        pref = preferences.autoContinue(),
        accentColor = accentColor,
    )

    CheckboxItem(
        label = "Background playback",
        pref = preferences.backgroundPlayback(),
        accentColor = accentColor,
    )

    CheckboxItem(
        label = "Show TTS controls",
        pref = preferences.showTtsControls(),
        accentColor = accentColor,
    )
}
