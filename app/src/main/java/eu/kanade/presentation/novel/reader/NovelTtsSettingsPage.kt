package eu.kanade.presentation.novel.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.novel.NovelTtsPreferences
import eu.kanade.tachiyomi.ui.reader.novel.tts.InstallState
import eu.kanade.tachiyomi.ui.reader.novel.tts.PocketVoice
import eu.kanade.tachiyomi.ui.reader.novel.tts.PocketVoiceCatalog
import eu.kanade.tachiyomi.ui.reader.novel.tts.SupertonicVoice
import eu.kanade.tachiyomi.ui.reader.novel.tts.SupertonicVoiceCatalog
import eu.kanade.tachiyomi.ui.reader.novel.tts.TtsVoice
import tachiyomi.presentation.core.util.collectAsState

/**
 * TTS settings page shown as a tab in the reader settings bottom sheet.
 */
@Composable
fun ColumnScope.NovelTtsSettingsPage(
    preferences: NovelTtsPreferences,
    accentColor: Color? = null,
    availableVoices: List<TtsVoice> = emptyList(),
    onRefreshVoices: () -> Unit = {},
    pocketInstallState: InstallState? = null,
    pocketVoices: List<PocketVoice> = emptyList(),
    onInstallPocket: () -> Unit = {},
    onUninstallPocket: () -> Unit = {},
    onSelectPocketVoice: (String) -> Unit = {},
    supertonicInstallState: InstallState? = null,
    supertonicVoices: List<SupertonicVoice> = emptyList(),
    onInstallSupertonic: () -> Unit = {},
    onUninstallSupertonic: () -> Unit = {},
    onSelectSupertonicVoice: (String) -> Unit = {},
) {
    // ---- Engine section ----
    SectionHeader("Text-to-Speech", accentColor)

    val engineType by preferences.engineType().collectAsState()
    val engineLabels = listOf("Android TTS (system)", "Pocket TTS", "Supertonic TTS")
    val engineValues = listOf("android", "pocket", "supertonic")
    SettingsDropdown(
        label = "TTS engine",
        // A stale stored value (e.g. the pre-port "neural") renders the first
        // entry instead of crashing — TtsController remaps it on next init (D-02).
        selectedLabel = engineLabels[engineValues.indexOf(engineType).coerceAtLeast(0)],
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
    if (engineType == "pocket") {
        SubHeader("Engine", accentColor)

        EngineInstallItem(
            state = pocketInstallState,
            accentColor = accentColor,
            sizeLabel = "~97 MB",
            onInstall = onInstallPocket,
            onUninstall = onUninstallPocket,
        )

        // ---- Voice picker (enabled only once the engine is installed) ----
        val voiceName by preferences.voiceName().collectAsState()
        if (pocketInstallState is InstallState.Installed || pocketInstallState is InstallState.Outdated) {
            if (pocketVoices.isNotEmpty()) {
                val voiceLabels = pocketVoices.map { PocketVoiceCatalog.labelFor(it) }
                // Blank or unknown selections resolve to the locked default
                // (eponine, D-08) for display; the engine does the same.
                val selectedIndex = pocketVoices.indexOfFirst { it.name == voiceName }
                    .takeIf { it >= 0 }
                    ?: pocketVoices.indexOfFirst { it.name == PocketVoiceCatalog.DEFAULT_VOICE }
                        .coerceAtLeast(0)
                SettingsDropdown(
                    label = "Voice",
                    selectedLabel = voiceLabels[selectedIndex],
                    options = voiceLabels,
                ) { index ->
                    onSelectPocketVoice(pocketVoices[index].name)
                }
            } else {
                SettingsCaption("No voices found — try reinstalling the engine")
            }

            // Voice attribution (REQ-006f) — the full notice lives in
            // About → Licenses; keep the picker itself free of legalese.
            SettingsCaption("Voice credits — About → Licenses")
        } else {
            SettingsCaption("Install the engine to pick a voice")
        }

        // ---- Advanced (collapsible) ----
        var showAdvanced by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAdvanced = !showAdvanced }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Advanced",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                imageVector = if (showAdvanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (showAdvanced) "Collapse" else "Expand",
                tint = accentColor ?: MaterialTheme.colorScheme.primary,
            )
        }

        AnimatedVisibility(
            visible = showAdvanced,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                // Performance overrides (D-09) — autodetect is the default;
                // these cover the long tail of exotic SoCs.
                val intraOpThreads by preferences.pocketIntraOpThreads().collectAsState()
                SliderItem(
                    label = "Synthesis threads",
                    value = intraOpThreads.coerceIn(0, 8),
                    valueRange = 0..8,
                    valueText = if (intraOpThreads == 0) "Auto" else "$intraOpThreads",
                    steps = 7,
                    accentColor = accentColor,
                ) { threads ->
                    preferences.pocketIntraOpThreads().set(threads.coerceIn(0, 8))
                }
                SettingsCaption("Auto matches your CPU. Only change this if speech is slow or the phone gets hot.")

                val xnnpackMode by preferences.pocketXnnpackMode().collectAsState()
                val xnnpackLabels = listOf("Auto", "On", "Off")
                val xnnpackValues = listOf("auto", "on", "off")
                SettingsDropdown(
                    label = "Hardware acceleration",
                    selectedLabel = xnnpackLabels[xnnpackValues.indexOf(xnnpackMode).coerceAtLeast(0)],
                    options = xnnpackLabels,
                ) { index ->
                    preferences.pocketXnnpackMode().set(xnnpackValues[index])
                }
                SettingsCaption("Leave on Auto — Off can help on phones where speech stutters.")
            }
        }
    } else if (engineType == "supertonic") {
        SubHeader("Engine", accentColor)

        EngineInstallItem(
            state = supertonicInstallState,
            accentColor = accentColor,
            sizeLabel = "~129 MB",
            onInstall = onInstallSupertonic,
            onUninstall = onUninstallSupertonic,
        )

        // ---- Voice picker (enabled only once the engine is installed) ----
        val voiceName by preferences.voiceName().collectAsState()
        if (supertonicInstallState is InstallState.Installed || supertonicInstallState is InstallState.Outdated) {
            if (supertonicVoices.isNotEmpty()) {
                val voiceLabels = supertonicVoices.map { SupertonicVoiceCatalog.labelFor(it) }
                // Blank or stale (e.g. pocket-era) selections resolve to the
                // locked default (supertonic:F5, D-05); the engine does the same (R6).
                val selectedIndex = supertonicVoices.indexOfFirst { it.name == voiceName }
                    .takeIf { it >= 0 }
                    ?: supertonicVoices.indexOfFirst { it.name == SupertonicVoiceCatalog.DEFAULT_VOICE }
                        .coerceAtLeast(0)
                SettingsDropdown(
                    label = "Voice",
                    selectedLabel = voiceLabels[selectedIndex],
                    options = voiceLabels,
                ) { index ->
                    onSelectSupertonicVoice(supertonicVoices[index].name)
                }
            } else {
                SettingsCaption("No voices found — try reinstalling the engine")
            }

            // Voice attribution (REQ-006f) — the full notice lives in
            // About → Licenses; keep the picker itself free of legalese.
            SettingsCaption("Voice credits — About → Licenses")
        } else {
            SettingsCaption("Install the engine to pick a voice")
        }

        // ---- Advanced (collapsible) ----
        var showAdvanced by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAdvanced = !showAdvanced }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Advanced",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                imageVector = if (showAdvanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (showAdvanced) "Collapse" else "Expand",
                tint = accentColor ?: MaterialTheme.colorScheme.primary,
            )
        }

        AnimatedVisibility(
            visible = showAdvanced,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                // Diffusion denoise steps (PB-21) — sherpa's proven default
                // is 5; the slider covers the V6 A/B tune range.
                val numSteps by preferences.supertonicNumSteps().collectAsState()
                SliderItem(
                    label = "Diffusion steps",
                    value = numSteps.coerceIn(3, 8),
                    valueRange = 3..8,
                    valueText = "$numSteps",
                    steps = 4,
                    accentColor = accentColor,
                ) { steps ->
                    preferences.supertonicNumSteps().set(steps.coerceIn(3, 8))
                }
                SettingsCaption("Higher is smoother but slower — 5 is the proven default.")

                // Performance overrides — the same shared ORT knobs the
                // pocket branch exposes (PB-5, RESEARCH rec 3).
                val intraOpThreads by preferences.pocketIntraOpThreads().collectAsState()
                SliderItem(
                    label = "Synthesis threads",
                    value = intraOpThreads.coerceIn(0, 8),
                    valueRange = 0..8,
                    valueText = if (intraOpThreads == 0) "Auto" else "$intraOpThreads",
                    steps = 7,
                    accentColor = accentColor,
                ) { threads ->
                    preferences.pocketIntraOpThreads().set(threads.coerceIn(0, 8))
                }
                SettingsCaption("Auto matches your CPU. Only change this if speech is slow or the phone gets hot.")

                val xnnpackMode by preferences.pocketXnnpackMode().collectAsState()
                val xnnpackLabels = listOf("Auto", "On", "Off")
                val xnnpackValues = listOf("auto", "on", "off")
                SettingsDropdown(
                    label = "Hardware acceleration",
                    selectedLabel = xnnpackLabels[xnnpackValues.indexOf(xnnpackMode).coerceAtLeast(0)],
                    options = xnnpackLabels,
                ) { index ->
                    preferences.pocketXnnpackMode().set(xnnpackValues[index])
                }
                SettingsCaption("Leave on Auto — Off can help on phones where speech stutters.")
            }
        }
    } else {
        // Android TTS: show system voice dropdown
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

/**
 * Small caption row used under settings items — same 16dp padding and
 * bodySmall/onSurfaceVariant styling as the rest of this page.
 */
@Composable
private fun SettingsCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/**
 * Engine install row — a standard settings row (16dp padding, bodyMedium
 * label, bodySmall subtitle) with a download-style trailing affordance that
 * mirrors extension/chapter downloads: download icon → progress → checkmark.
 * Tapping the checkmark opens a menu holding the destructive action
 * ("Remove"), the same pattern as downloaded chapters.
 */
@Composable
private fun EngineInstallItem(
    state: InstallState?,
    accentColor: Color?,
    sizeLabel: String,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
) {
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    var menuOpen by remember { mutableStateOf(false) }
    val retryable = state is InstallState.Failed || state is InstallState.Corrupt

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = state == null || state == InstallState.NotInstalled || retryable) {
                onInstall()
            }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Voice pack",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            when (state) {
                null, InstallState.NotInstalled -> {
                    SettingsInlineCaption("Not installed · $sizeLabel")
                }
                is InstallState.Downloading -> {
                    val total = state.totalBytes.coerceAtLeast(1L)
                    val fraction = (state.bytesFetched.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    SettingsInlineCaption("Downloading… ${(fraction * 100).toInt()}%")
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        color = accent,
                    )
                }
                is InstallState.Extracting -> {
                    SettingsInlineCaption("Preparing voices…")
                    LinearProgressIndicator(
                        progress = {
                            if (state.totalBytes > 0L) {
                                (state.bytesExtracted.toFloat() / state.totalBytes.toFloat())
                                    .coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        color = accent,
                    )
                }
                InstallState.Installed -> {
                    SettingsInlineCaption("Installed")
                }
                is InstallState.Outdated -> {
                    SettingsInlineCaption("Installed · update available")
                }
                is InstallState.Failed -> {
                    Text(
                        text = "Couldn't download — tap to retry",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                InstallState.Corrupt -> {
                    Text(
                        text = "Install incomplete — tap to repair",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        when (state) {
            null, InstallState.NotInstalled -> {
                IconButton(onClick = onInstall) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = "Install voice pack",
                        tint = accent,
                    )
                }
            }
            is InstallState.Downloading, is InstallState.Extracting -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = accent,
                    strokeWidth = 2.dp,
                )
            }
            InstallState.Installed, is InstallState.Outdated -> {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Installed — open options",
                            tint = accent,
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        if (state is InstallState.Outdated) {
                            DropdownMenuItem(
                                text = { Text("Update") },
                                onClick = {
                                    menuOpen = false
                                    onInstall()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Remove") },
                            onClick = {
                                menuOpen = false
                                onUninstall()
                            },
                        )
                    }
                }
            }
            is InstallState.Failed, InstallState.Corrupt -> {
                IconButton(onClick = onInstall) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Retry install",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/**
 * Subtitle text inside [EngineInstallItem]'s row — no extra padding since the
 * row already carries the page's 16dp inset.
 */
@Composable
private fun SettingsInlineCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
