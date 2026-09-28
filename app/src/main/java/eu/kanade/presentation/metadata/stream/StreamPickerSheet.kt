package eu.kanade.presentation.metadata.stream

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.metadata.miko.dto.StremioStreamDto
import eu.kanade.tachiyomi.metadata.stream.StreamResolver
import eu.kanade.tachiyomi.ui.metadata.stream.StreamResolverScreenModel
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Bottom sheet showing available streaming sources for a Cinemeta entry.
 * The user picks a source, then episodes are fetched and the player launches.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamPickerSheet(
    state: StreamResolverScreenModel.State,
    onDismiss: () -> Unit,
    onCandidateSelected: (StreamResolver.StreamCandidate) -> Unit,
    onSearchManually: () -> Unit,
    onChangeSource: (() -> Unit)? = null,
    onEpisodeSelected: ((tachiyomi.domain.items.episode.model.Episode) -> Unit)? = null,
    downloadMode: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            // Header: title + close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (downloadMode) "Download from" else "Sources",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                    )
                }
            }

            when (state) {
                is StreamResolverScreenModel.State.Loading -> {
                    LoadingContent()
                }
                is StreamResolverScreenModel.State.Searching -> {
                    SearchingContent(
                        // Torrent/Stremio candidates can't be downloaded —
                        // download mode only lists extension sources.
                        candidates = if (downloadMode) {
                            state.candidates.filterIsInstance<StreamResolver.StreamCandidate.Extension>()
                        } else {
                            state.candidates
                        },
                        searching = state.searching,
                        providerStatuses = state.providerStatuses,
                        onCandidateSelected = onCandidateSelected,
                        timedOut = state.timedOut,
                        failedSources = state.failedSources,
                        onChangeSource = if (downloadMode) null else onChangeSource,
                    )
                }
                is StreamResolverScreenModel.State.Resolving -> {
                    ResolvingContent(candidate = state.candidate)
                }
                is StreamResolverScreenModel.State.Empty -> {
                    EmptyContent(onSearchManually = onSearchManually)
                }
                is StreamResolverScreenModel.State.Error -> {
                    ErrorContent(
                        message = state.message,
                        onSearchManually = onSearchManually,
                    )
                }
                is StreamResolverScreenModel.State.Idle -> {
                    // Should not happen when sheet is visible
                }
                is StreamResolverScreenModel.State.EpisodeResolved -> {
                    // Handled by ShowsScreen — sheet dismisses
                }
                is StreamResolverScreenModel.State.NoMatch -> {
                    NoMatchContent(
                        episodes = state.episodes,
                        onEpisodeSelected = onEpisodeSelected,
                        onSearchManually = onSearchManually,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchingContent(
    candidates: List<StreamResolver.StreamCandidate>,
    searching: Boolean,
    providerStatuses: Map<String, StreamResolver.ProviderStatus>,
    onCandidateSelected: (StreamResolver.StreamCandidate) -> Unit,
    timedOut: Boolean,
    failedSources: List<String>,
    onChangeSource: (() -> Unit)?,
) {
    var selectedProvider by rememberSaveable { mutableStateOf<String?>(null) }

    // Provider pills: one per known provider (registered upfront), greyed out
    // until that provider has returned. Failed providers stay greyed — the
    // footer lists them by name.
    if (providerStatuses.size > 1) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = selectedProvider == null,
                    onClick = { selectedProvider = null },
                    label = { Text("All") },
                )
            }
            items(providerStatuses.entries.toList()) { (provider, status) ->
                FilterChip(
                    selected = selectedProvider == provider,
                    enabled = status == StreamResolver.ProviderStatus.DONE,
                    onClick = {
                        selectedProvider = if (selectedProvider == provider) null else provider
                    },
                    label = { Text(provider) },
                    leadingIcon = if (status == StreamResolver.ProviderStatus.LOADING) {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }

    val filtered = candidates.filter {
        selectedProvider == null || it.providerName() == selectedProvider
    }
    CandidatesList(
        candidates = filtered,
        onCandidateSelected = onCandidateSelected,
        searching = searching,
        timedOut = timedOut,
        failedSources = failedSources,
        onChangeSource = onChangeSource,
    )
}

private fun StreamResolver.StreamCandidate.providerName(): String = when (this) {
    is StreamResolver.StreamCandidate.Stremio -> addonName
    is StreamResolver.StreamCandidate.Extension -> source.name
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = "Searching sources...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResolvingContent(candidate: StreamResolver.StreamCandidate) {
    val loadingText = when (candidate) {
        is StreamResolver.StreamCandidate.Stremio -> "Resolving torrent stream from ${candidate.addonName}..."
        is StreamResolver.StreamCandidate.Extension -> "Loading episodes from ${candidate.source.name}..."
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = loadingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CandidatesList(
    candidates: List<StreamResolver.StreamCandidate>,
    onCandidateSelected: (StreamResolver.StreamCandidate) -> Unit,
    searching: Boolean = false,
    timedOut: Boolean = false,
    failedSources: List<String> = emptyList(),
    onChangeSource: (() -> Unit)? = null,
) {
    val hasCached = candidates.any { it.cached }
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(candidates) { candidate ->
            CandidateRow(
                candidate = candidate,
                onClick = { onCandidateSelected(candidate) },
            )
            HorizontalDivider()
        }
        if (hasCached && onChangeSource != null) {
            item {
                TextButton(
                    onClick = onChangeSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text("Change source")
                }
            }
        }
        if (searching && candidates.isEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = "  Searching sources...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (!searching && (timedOut || failedSources.isNotEmpty())) {
            item {
                val message = buildString {
                    if (timedOut) append("Some sources are taking too long.")
                    if (failedSources.isNotEmpty()) {
                        if (isNotEmpty()) append(" ")
                        append("Failed: ${failedSources.joinToString(", ")}")
                    }
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/**
 * Extract a display-quality label ("1080p HDR") from a Stremio stream's
 * name/title fields. Falls back to the stream's own name.
 */
private fun qualityLabel(stream: StremioStreamDto): String {
    val haystack = listOfNotNull(stream.name, stream.title, stream.filename).joinToString("\n")
    val resolution = QUALITY_RES_REGEX.find(haystack)?.value?.uppercase()
    val tags = QUALITY_TAG_REGEX.findAll(haystack)
        .map { it.value.uppercase() }
        .distinct()
        .take(2)
        .toList()
    return listOfNotNull(resolution, tags.joinToString(" ").takeIf { it.isNotBlank() })
        .joinToString(" ")
        .ifBlank {
            stream.name?.lineSequence()?.lastOrNull()?.takeIf { it.isNotBlank() }
                ?: stream.filename
                ?: "Stream"
        }
}

private val QUALITY_RES_REGEX = Regex("(?i)\\b(8k|4k|2160p|1440p|1080p|720p|540p|480p|360p|240p)\\b")
private val QUALITY_TAG_REGEX = Regex(
    "(?i)\\b(HDR10\\+|HDR|Dolby Vision|DV|SDR|IMAX|REMUX|CAM|TS|WEB-?DL|WEBRip|BluRay|BD|HDTV)\\b",
)

@Composable
private fun CandidateRow(
    candidate: StreamResolver.StreamCandidate,
    onClick: () -> Unit,
) {
    val title: String
    val subtitle: String
    val badge: String

    when (candidate) {
        is StreamResolver.StreamCandidate.Stremio -> {
            title = qualityLabel(candidate.stream)
            val fileInfo = candidate.stream.filename
                ?: candidate.stream.title?.lineSequence()?.firstOrNull()
            subtitle = listOfNotNull(candidate.addonName, fileInfo)
                .joinToString(" · ")
                .replace("\n", " ")
            badge = if (candidate.cached) "Cached" else "Torrent"
        }
        is StreamResolver.StreamCandidate.Extension -> {
            // The row shows what the source actually serves (its anime entry);
            // the target episode name would falsely imply a resolved match.
            title = candidate.animeTitle
            subtitle = candidate.source.name
            val scorePercent = (candidate.matchScore * 100).toInt()
            badge = if (candidate.cached) "Cached" else "$scorePercent%"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CandidateIcon(candidate)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = badge,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CandidateIcon(candidate: StreamResolver.StreamCandidate) {
    val iconModifier = Modifier
        .size(40.dp)
        .clip(CircleShape)
    when (candidate) {
        is StreamResolver.StreamCandidate.Stremio -> {
            if (candidate.addonLogoUrl != null) {
                AsyncImage(
                    model = candidate.addonLogoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = iconModifier,
                )
            } else {
                FallbackIcon(iconModifier)
            }
        }
        is StreamResolver.StreamCandidate.Extension -> {
            val icon = remember(candidate.source.id) {
                Injekt.get<AnimeExtensionManager>()
                    .getAppIconForSource(candidate.source.id)
                    ?.toBitmap()
                    ?.asImageBitmap()
            }
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = iconModifier,
                )
            } else {
                FallbackIcon(iconModifier)
            }
        }
    }
}

@Composable
private fun FallbackIcon(modifier: Modifier) {
    Icon(
        imageVector = Icons.Outlined.PlayCircle,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

@Composable
private fun EmptyContent(onSearchManually: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "No streaming sources found",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = "Try installing more anime extensions to find streams for this title.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onSearchManually) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(" Search manually")
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onSearchManually: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Error",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onSearchManually) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(" Search manually")
        }
    }
}

@Composable
private fun NoMatchContent(
    episodes: List<tachiyomi.domain.items.episode.model.Episode>,
    onEpisodeSelected: ((tachiyomi.domain.items.episode.model.Episode) -> Unit)?,
    onSearchManually: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Couldn't auto-match the episode. Pick one manually:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(episodes) { episode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onEpisodeSelected?.invoke(episode)
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = episode.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Episode ${eu.kanade.presentation.util.formatEpisodeNumber(episode.episodeNumber)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
        TextButton(
            onClick = onSearchManually,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(" Search manually")
        }
    }
}
