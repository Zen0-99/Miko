package eu.kanade.tachiyomi.ui.player.controls.components.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import eu.kanade.presentation.player.components.ExpandableCard
import eu.kanade.tachiyomi.ui.player.controls.CARDS_MAX_WIDTH
import eu.kanade.tachiyomi.ui.player.controls.panelCardsColors
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import `is`.xyz.mpv.MPVLib
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

enum class SubtitlePreset {
    Default,
    Yellow,
    HighContrast,
    Large,
    ;

    fun apply(preferences: SubtitlePreferences) {
        val (textColor, borderColor, bgColor, bold, fontSize, fontScale) = when (this) {
            Default -> quad(Color.White.toArgb(), Color.Black.toArgb(), Color.Transparent.toArgb(), false, 55, 1f)
            Yellow -> quad(Color(0xFFFFEB3B).toArgb(), Color.Black.toArgb(), Color.Transparent.toArgb(), false, 55, 1f)
            HighContrast -> quad(
                Color.White.toArgb(),
                Color.Black.toArgb(),
                Color(0x80000000).toArgb(),
                true,
                55,
                1.3f,
            )
            Large -> quad(Color.White.toArgb(), Color.Black.toArgb(), Color.Transparent.toArgb(), false, 80, 1f)
        }

        preferences.textColorSubtitles().set(textColor)
        preferences.borderColorSubtitles().set(borderColor)
        preferences.backgroundColorSubtitles().set(bgColor)
        preferences.boldSubtitles().set(bold)
        preferences.subtitleFontSize().set(fontSize)
        preferences.subtitleFontScale().set(fontScale)

        MPVLib.setPropertyString("sub-color", textColor.toColorHexString())
        MPVLib.setPropertyString("sub-border-color", borderColor.toColorHexString())
        MPVLib.setPropertyString("sub-back-color", bgColor.toColorHexString())
        MPVLib.setPropertyString("sub-bold", if (bold) "yes" else "no")
        MPVLib.setPropertyString("sub-font-size", fontSize.toString())
        MPVLib.setPropertyString("sub-scale", fontScale.toString())
    }

    val titleRes
        get() = when (this) {
            Default -> AYMR.strings.player_subtitle_preset_default
            Yellow -> AYMR.strings.player_subtitle_preset_yellow
            HighContrast -> AYMR.strings.player_subtitle_preset_high_contrast
            Large -> AYMR.strings.player_subtitle_preset_large
        }
}

private fun quad(
    textColor: Int,
    borderColor: Int,
    bgColor: Int,
    bold: Boolean,
    fontSize: Int,
    fontScale: Float,
) = SubtitlePresetConfig(textColor, borderColor, bgColor, bold, fontSize, fontScale)

private data class SubtitlePresetConfig(
    val textColor: Int,
    val borderColor: Int,
    val bgColor: Int,
    val bold: Boolean,
    val fontSize: Int,
    val fontScale: Float,
)

@Composable
fun SubtitlePresetsRow(
    modifier: Modifier = Modifier,
) {
    val preferences = remember { Injekt.get<SubtitlePreferences>() }
    var isExpanded by remember { mutableStateOf(true) }

    ExpandableCard(
        isExpanded = isExpanded,
        onExpand = { isExpanded = !isExpanded },
        title = {
            Text(stringResource(AYMR.strings.player_subtitle_presets_title))
        },
        modifier = modifier.widthIn(max = CARDS_MAX_WIDTH),
        colors = panelCardsColors(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.small),
        ) {
            SubtitlePreset.entries.forEach { preset ->
                FilterChip(
                    selected = false,
                    onClick = { preset.apply(preferences) },
                    label = { Text(stringResource(preset.titleRes)) },
                )
            }
        }
    }
}
