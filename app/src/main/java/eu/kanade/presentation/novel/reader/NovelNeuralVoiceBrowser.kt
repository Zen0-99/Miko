package eu.kanade.presentation.novel.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.novel.tts.InstalledNeuralVoice
import eu.kanade.tachiyomi.ui.reader.novel.tts.NeuralTtsVoiceCatalog
import eu.kanade.tachiyomi.ui.reader.novel.tts.NeuralVoiceEntry

/**
 * Neural TTS voice browser and downloader, shown as a section in the TTS
 * settings page when the neural engine is selected.
 *
 * Lists voices from the bundled catalog, shows installation state, and
 * provides download/uninstall actions.
 */
@Composable
fun ColumnScope.NeuralVoiceBrowser(
    installedVoices: List<InstalledNeuralVoice>,
    downloadingVoiceId: String?,
    downloadProgress: Float,
    accentColor: Color? = null,
    onDownloadVoice: (NeuralVoiceEntry) -> Unit,
    onUninstallVoice: (String) -> Unit,
    onSelectVoice: (InstalledNeuralVoice) -> Unit,
    selectedVoiceId: String,
) {
    val accent = accentColor ?: MaterialTheme.colorScheme.primary

    SubHeader("Neural Voices", accentColor)

    Text(
        text = "Download neural TTS voices for offline, high-quality speech synthesis. " +
            "Voices are typically 30-100 MB each.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )

    // Group voices by language for easier browsing
    val grouped = NeuralTtsVoiceCatalog.VOICE_CATALOG
        .groupBy { it.language.substringBefore("-") }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        grouped.forEach { (lang, voices) ->
            item {
                Text(
                    text = lang.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp),
                )
            }
            items(voices, key = { it.id }) { entry ->
                VoiceRow(
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
    }
}

@Composable
private fun VoiceRow(
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface,
                )
                if (isInstalled && isSelected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = accentColor,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                text = buildString {
                    append(entry.family.replaceFirstChar { it.uppercase() })
                    append(" · ")
                    append(entry.language)
                    if (entry.gender.isNotEmpty()) {
                        append(" · ")
                        append(if (entry.gender == "M") "Male" else "Female")
                    }
                    append(" · ~")
                    append(entry.approxSizeMb)
                    append(" MB")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isDownloading) {
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
        }

        Box(
            contentAlignment = Alignment.Center,
        ) {
            when {
                isDownloading -> {
                    val indicatorSize = 26.dp
                    val indicatorPadding = 2.dp
                    val arrowColor = if (downloadProgress >= 0.5f) {
                        MaterialTheme.colorScheme.background
                    } else {
                        accentColor
                    }
                    if (downloadProgress <= 0f) {
                        // Indeterminate (queueing/starting)
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(indicatorSize)
                                .padding(indicatorPadding),
                            color = accentColor,
                            strokeWidth = indicatorPadding,
                            trackColor = Color.Transparent,
                            strokeCap = StrokeCap.Butt,
                        )
                    } else {
                        val animatedProgress by animateFloatAsState(
                            targetValue = downloadProgress,
                            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                            label = "voice_download_progress",
                        )
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .size(indicatorSize)
                                .padding(indicatorPadding),
                            color = accentColor,
                            strokeWidth = indicatorSize / 2,
                            trackColor = Color.Transparent,
                            strokeCap = StrokeCap.Butt,
                            gapSize = 0.dp,
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.ArrowDownward,
                        contentDescription = "Downloading",
                        modifier = Modifier.size(indicatorSize - 7.dp),
                        tint = arrowColor,
                    )
                }
                isInstalled -> {
                    // Show a "downloaded" icon (like chapters) that opens a context menu
                    var isMenuExpanded by remember { mutableStateOf(false) }
                    val indicatorSize = 26.dp
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { isMenuExpanded = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DownloadDone,
                            contentDescription = "Installed",
                            modifier = Modifier.size(indicatorSize),
                            tint = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        DropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { isMenuExpanded = false },
                        ) {
                            if (!isSelected) {
                                DropdownMenuItem(
                                    text = { Text("Select voice") },
                                    onClick = {
                                        onSelect()
                                        isMenuExpanded = false
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete voice") },
                                onClick = {
                                    onUninstall()
                                    isMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                else -> {
                    // Download button — chapter-style circle with arrow
                    val indicatorSize = 26.dp
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { onDownload() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = "Download",
                            modifier = Modifier.size(indicatorSize),
                            tint = accentColor,
                        )
                    }
                }
            }
        }
    }
}
