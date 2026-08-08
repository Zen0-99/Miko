package eu.kanade.presentation.novel.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.kanade.tachiyomi.ui.reader.novel.NovelTtsPreferences
import eu.kanade.tachiyomi.ui.reader.novel.tts.InstalledNeuralVoice
import eu.kanade.tachiyomi.ui.reader.novel.tts.NeuralTtsVoiceCatalog
import eu.kanade.tachiyomi.ui.reader.novel.tts.NeuralVoiceEntry
import tachiyomi.presentation.core.util.collectAsState

/**
 * TTS settings page shown as a tab in the reader settings bottom sheet.
 */
@Composable
fun ColumnScope.NovelTtsSettingsPage(
    preferences: NovelTtsPreferences,
    accentColor: Color? = null,
    availableVoices: List<eu.kanade.tachiyomi.ui.reader.novel.tts.TtsVoice> = emptyList(),
    onRefreshVoices: () -> Unit = {},
    installedNeuralVoices: List<InstalledNeuralVoice> = emptyList(),
    downloadingVoiceId: String? = null,
    downloadProgress: Float = 0f,
    onDownloadVoice: (NeuralVoiceEntry) -> Unit = {},
    onUninstallVoice: (String) -> Unit = {},
    onSelectNeuralVoice: (InstalledNeuralVoice) -> Unit = {},
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
    if (engineType == "neural") {
        val selectedVoiceId by preferences.voiceName().collectAsState()
        var showVoiceBrowser by remember { mutableStateOf(false) }

        val selectedVoiceName = remember(selectedVoiceId, installedNeuralVoices) {
            if (selectedVoiceId.isBlank()) {
                "None"
            } else {
                NeuralTtsVoiceCatalog.VOICE_CATALOG
                    .firstOrNull { it.id == selectedVoiceId }
                    ?.displayName
                    ?: installedNeuralVoices.firstOrNull { it.voiceId == selectedVoiceId }?.displayName
                    ?: "None"
            }
        }

        // Button box that opens the full-screen voice browser
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable { showVoiceBrowser = true }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Neural Voices",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = selectedVoiceName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Browse",
                style = MaterialTheme.typography.labelMedium,
                color = accentColor ?: MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }

        if (showVoiceBrowser) {
            NeuralVoiceBrowserDialog(
                installedVoices = installedNeuralVoices,
                downloadingVoiceId = downloadingVoiceId,
                downloadProgress = downloadProgress,
                accentColor = accentColor,
                onDownloadVoice = onDownloadVoice,
                onUninstallVoice = onUninstallVoice,
                onSelectVoice = onSelectNeuralVoice,
                selectedVoiceId = selectedVoiceId,
                onDismiss = { showVoiceBrowser = false },
            )
        }

        // ---- Advanced Neural TTS Settings (collapsible) ----
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
                text = "Advanced Neural TTS Settings",
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
                // Performance settings
                SubHeader("Performance", accentColor)

                CheckboxItem(
                    label = "NNAPI hardware acceleration",
                    pref = preferences.neuralUseNnapi(),
                    accentColor = accentColor,
                )

                val numThreads by preferences.neuralNumThreads().collectAsState()
                SliderItem(
                    label = "Synthesis threads",
                    value = numThreads,
                    valueRange = 1..8,
                    valueText = "$numThreads",
                    accentColor = accentColor,
                ) { threads ->
                    preferences.neuralNumThreads().set(threads)
                }

                val maxSentences by preferences.neuralMaxSentences().collectAsState()
                SliderItem(
                    label = "Max sentences per call",
                    value = maxSentences,
                    valueRange = 1..4,
                    valueText = "$maxSentences",
                    accentColor = accentColor,
                ) { max ->
                    preferences.neuralMaxSentences().set(max)
                }

                // Playback timing
                SubHeader("Playback Timing", accentColor)

                val sentencePause by preferences.neuralSentencePauseMs().collectAsState()
                SliderItem(
                    label = "Sentence pause",
                    value = sentencePause,
                    valueRange = 0..500,
                    valueText = if (sentencePause == 0) "Off" else "${sentencePause}ms",
                    accentColor = accentColor,
                ) { pause ->
                    preferences.neuralSentencePauseMs().set(pause)
                }
                Text(
                    text = "Silence inserted between sentences. Neural TTS " +
                        "concatenates sentences with no gap — increase this " +
                        "for natural breathing pauses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )

                // Voice tuning (VITS/Piper only)
                SubHeader("Voice Tuning (Piper/VITS only)", accentColor)

                val noiseScale by preferences.neuralNoiseScale().collectAsState()
                FloatSliderItem(
                    label = "Noise scale",
                    value = noiseScale,
                    valueRange = 0f..1f,
                    valueText = String.format("%.3f", noiseScale),
                    steps = 19,
                    accentColor = accentColor,
                ) { ns ->
                    preferences.neuralNoiseScale().set(ns)
                }
                Text(
                    text = "Controls expressiveness of the generated speech. " +
                        "Higher values produce more natural variation; lower values " +
                        "sound more monotone. Default: 0.667",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )

                val noiseScaleW by preferences.neuralNoiseScaleW().collectAsState()
                FloatSliderItem(
                    label = "Noise scale W",
                    value = noiseScaleW,
                    valueRange = 0f..1f,
                    valueText = String.format("%.3f", noiseScaleW),
                    steps = 19,
                    accentColor = accentColor,
                ) { nsw ->
                    preferences.neuralNoiseScaleW().set(nsw)
                }
                Text(
                    text = "Controls variation in phoneme durations. " +
                        "Higher values add more natural rhythm variation; lower values " +
                        "produce more consistent timing. Default: 0.8",
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

// ---- Voice quality/speed ratings derived from family ----

private fun voiceQualityRating(family: String): String = when (family) {
    "piper" -> "Good"
    "kokoro" -> "Highest"
    "matcha" -> "Good"
    "kitten" -> "Compact"
    "zipvoice" -> "Lower"
    else -> "—"
}

private fun voiceSpeedRating(family: String): String = when (family) {
    "piper" -> "Fast"
    "kokoro" -> "Slower"
    "matcha" -> "Medium"
    "kitten" -> "Fast"
    "zipvoice" -> "Fast"
    else -> "—"
}

private fun familyDisplay(family: String): String = when (family) {
    "piper" -> "Piper"
    "kokoro" -> "Kokoro"
    "matcha" -> "Matcha"
    "kitten" -> "Kitten"
    "zipvoice" -> "ZipVoice"
    else -> family.replaceFirstChar { it.uppercase() }
}

/**
 * Full-screen dialog for browsing and searching neural TTS voices.
 * Uses a DockedSearchBar integrated into the top bar area, and card-based
 * voice items with quality/speed ratings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NeuralVoiceBrowserDialog(
    installedVoices: List<InstalledNeuralVoice>,
    downloadingVoiceId: String?,
    downloadProgress: Float,
    accentColor: Color? = null,
    onDownloadVoice: (NeuralVoiceEntry) -> Unit,
    onUninstallVoice: (String) -> Unit,
    onSelectVoice: (InstalledNeuralVoice) -> Unit,
    selectedVoiceId: String,
    onDismiss: () -> Unit,
) {
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (searchActive) {
                            // When search is active, the DockedSearchBar below
                            // handles the input; show empty title
                        } else {
                            Text("Neural Voices")
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { searchActive = !searchActive }) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                // Search bar — shown when search is toggled on
                if (searchActive) {
                    androidx.compose.material3.OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Search voices...") },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.ExpandLess, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    )
                }

                // Filter voices by search query
                val filteredVoices = remember(searchQuery) {
                    if (searchQuery.isBlank()) {
                        NeuralTtsVoiceCatalog.VOICE_CATALOG
                    } else {
                        val query = searchQuery.lowercase()
                        NeuralTtsVoiceCatalog.VOICE_CATALOG.filter { entry ->
                            entry.displayName.lowercase().contains(query) ||
                                entry.language.lowercase().contains(query) ||
                                entry.family.lowercase().contains(query) ||
                                entry.id.lowercase().contains(query)
                        }
                    }
                }

                // Group filtered voices by language
                val grouped = filteredVoices.groupBy { it.language.substringBefore("-") }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (filteredVoices.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "No voices found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    grouped.forEach { (lang, voices) ->
                        item {
                            Text(
                                text = fullLanguageName(lang),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
                            )
                        }
                        items(voices, key = { it.id }) { entry ->
                            VoiceCard(
                                entry = entry,
                                isInstalled = installedVoices.any { it.voiceId == entry.id },
                                isSelected = selectedVoiceId == entry.id,
                                isDownloading = downloadingVoiceId == entry.id,
                                downloadProgress = if (downloadingVoiceId == entry.id) downloadProgress else 0f,
                                accentColor = accent,
                                onDownload = { onDownloadVoice(entry) },
                                onUninstall = { onUninstallVoice(entry.id) },
                                onSelect = {
                                    installedVoices.firstOrNull { it.voiceId == entry.id }
                                        ?.let(onSelectVoice)
                                },
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

private fun fullLanguageName(code: String): String = when (code) {
    "en" -> "English"
    "zh" -> "Chinese"
    "ja" -> "Japanese"
    "ko" -> "Korean"
    "es" -> "Spanish"
    "fr" -> "French"
    "de" -> "German"
    "multi" -> "Multilingual"
    else -> code.uppercase()
}

/**
 * Card-based voice item with quality/speed ratings.
 *
 * Layout:
 * - Title: "Amy" + family suffix in slightly darker font (" · Piper")
 * - Sub-text 1: "Q: Good  S: Fast" quality and speed ratings
 * - Sub-text 2: Gender · Size (e.g. "Female · ~61 MB")
 * - Action: download/install/select/delete button on the right
 */
@Composable
private fun VoiceCard(
    entry: NeuralVoiceEntry,
    isInstalled: Boolean,
    isSelected: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    accentColor: Color,
    onDownload: () -> Unit,
    onUninstall: () -> Unit,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                accentColor.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        onClick = if (isInstalled) onSelect else ({ }),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                // Title: voice name + family suffix
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = entry.displayName.substringBefore(" ("),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "· ${familyDisplay(entry.family)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Quality and speed ratings
                Text(
                    text = "Q: ${voiceQualityRating(entry.family)}  ·  S: ${voiceSpeedRating(entry.family)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Gender and size
                val genderText = when (entry.gender) {
                    "M" -> "Male"
                    "F" -> "Female"
                    else -> ""
                }
                val detailText = buildString {
                    if (genderText.isNotEmpty()) {
                        append(genderText)
                        append(" · ")
                    }
                    append("~")
                    append(entry.approxSizeMb)
                    append(" MB")
                }
                Text(
                    text = detailText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )

                if (isDownloading) {
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        color = accentColor,
                    )
                }
            }

            // Action button
            Box(
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isDownloading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            color = accentColor,
                            strokeWidth = 2.dp,
                        )
                    }
                    isInstalled && isSelected -> {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Selected",
                            tint = accentColor,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    isInstalled -> {
                        Row {
                            IconButton(onClick = onSelect) {
                                Icon(
                                    imageVector = Icons.Filled.DownloadDone,
                                    contentDescription = "Select",
                                    tint = accentColor,
                                )
                            }
                            IconButton(onClick = onUninstall) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Uninstall",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    else -> {
                        IconButton(onClick = onDownload) {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = "Download",
                                tint = accentColor,
                            )
                        }
                    }
                }
            }
        }
    }
}
