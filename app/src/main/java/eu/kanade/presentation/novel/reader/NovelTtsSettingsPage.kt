package eu.kanade.presentation.novel.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import eu.kanade.tachiyomi.ui.reader.novel.tts.PocketEngineCatalog
import eu.kanade.tachiyomi.ui.reader.novel.tts.PocketVoice
import eu.kanade.tachiyomi.ui.reader.novel.tts.PocketVoiceCatalog
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
) {
    // ---- Engine section ----
    SectionHeader("Text-to-Speech", accentColor)

    val engineType by preferences.engineType().collectAsState()
    val engineLabels = listOf("Android TTS (system)", "Pocket TTS (neural)")
    val engineValues = listOf("android", "pocket")
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

        PocketEngineInstallCard(
            state = pocketInstallState,
            accentColor = accentColor,
            onInstall = onInstallPocket,
            onUninstall = onUninstallPocket,
        )

        // ---- Voice picker (enabled only once the engine is installed) ----
        val voiceName by preferences.voiceName().collectAsState()
        if (pocketInstallState is InstallState.Installed || pocketInstallState is InstallState.Outdated) {
            if (pocketVoices.isNotEmpty()) {
                val voiceLabels = pocketVoices.map { voice ->
                    buildString {
                        append(voice.displayName)
                        if (voice.license.isNotBlank()) {
                            append(" · ")
                            append(voice.license)
                        }
                    }
                }
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
                Text(
                    text = "No voices found in the installed bundle",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // Voice attribution (REQ-006f) — every shipped voice carries its
            // license; the full notice lives in About → Licenses.
            Text(
                text = "Voices: alba, azelma, eponine, fantine — CC-BY-4.0 " +
                    "(VCTK Corpus, U. Edinburgh / Alba Mackenna) · " +
                    "javert, marius — CC0 (Unmute Voice Donation Project) · " +
                    "Details in About → Licenses",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        } else {
            Text(
                text = "Install the Pocket engine to choose a voice",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // ---- Advanced Pocket TTS Settings (collapsible) ----
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
                text = "Advanced Pocket TTS Settings",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
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
                SubHeader("Performance", accentColor)

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
                Text(
                    text = "ONNX Runtime intra-op thread count. Auto detects the " +
                        "CPU's performance cluster; override only if synthesis " +
                        "is slow or the device overheats.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )

                val xnnpackMode by preferences.pocketXnnpackMode().collectAsState()
                val xnnpackLabels = listOf("Auto", "On", "Off")
                val xnnpackValues = listOf("auto", "on", "off")
                SettingsDropdown(
                    label = "XNNPACK acceleration",
                    selectedLabel = xnnpackLabels[xnnpackValues.indexOf(xnnpackMode).coerceAtLeast(0)],
                    options = xnnpackLabels,
                ) { index ->
                    preferences.pocketXnnpackMode().set(xnnpackValues[index])
                }
                Text(
                    text = "XNNPACK execution provider. Auto tries it with a CPU " +
                        "fallback; On forces it; Off uses the CPU provider only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )

                Spacer(modifier = Modifier.height(8.dp))
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
 * Install card for the Pocket TTS engine bundle — mirrors the voice-download
 * card UX: title + download size on top, state-driven content below
 * (D-04).
 */
@Composable
private fun PocketEngineInstallCard(
    state: InstallState?,
    accentColor: Color?,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
) {
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = PocketEngineCatalog.POCKET_TTS_EN.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "~97 MB download · engine MIT (Kyutai), voices CC-BY-4.0/CC0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (state) {
                null, InstallState.NotInstalled -> {
                    Button(onClick = onInstall) {
                        Text("Install")
                    }
                }
                is InstallState.Downloading -> {
                    val total = state.totalBytes.coerceAtLeast(1L)
                    LinearProgressIndicator(
                        progress = { (state.bytesFetched.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = accent,
                    )
                    Text(
                        text = String.format(
                            "Downloading — %.0f / %.0f MB",
                            state.bytesFetched / 1_000_000f,
                            state.totalBytes / 1_000_000f,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                is InstallState.Extracting -> {
                    if (state.totalBytes > 0L) {
                        LinearProgressIndicator(
                            progress = {
                                (state.bytesExtracted.toFloat() / state.totalBytes.toFloat())
                                    .coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = accent,
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = accent,
                        )
                    }
                    Text(
                        text = "Extracting…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                InstallState.Installed -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Installed",
                                tint = accent,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = "Installed",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        TextButton(onClick = onUninstall) {
                            Text("Uninstall")
                        }
                    }
                }
                is InstallState.Failed -> {
                    Text(
                        text = "Install failed: ${state.reason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onInstall) {
                        Text("Retry")
                    }
                }
                InstallState.Corrupt -> {
                    Text(
                        text = "Reinstall required — the installed files are incomplete.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onInstall) {
                        Text("Reinstall")
                    }
                }
                is InstallState.Outdated -> {
                    Text(
                        text = "Update available — the installed bundle predates the current catalog. The existing install keeps working.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onInstall) {
                        Text("Update")
                    }
                }
            }
        }
    }
}
