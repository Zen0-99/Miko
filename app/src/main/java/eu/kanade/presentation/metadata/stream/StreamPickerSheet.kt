package eu.kanade.presentation.metadata.stream

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.metadata.stream.StreamResolver
import eu.kanade.tachiyomi.ui.metadata.stream.StreamResolverScreenModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

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
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            // Title
            Text(
                text = stringResource(MR.strings.label_extensions),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            )

            when (state) {
                is StreamResolverScreenModel.State.Loading -> {
                    LoadingContent()
                }
                is StreamResolverScreenModel.State.Success -> {
                    CandidatesList(
                        candidates = state.candidates,
                        onCandidateSelected = { candidate ->
                            onCandidateSelected(candidate)
                        },
                        timedOut = state.timedOut,
                        failedSources = state.failedSources,
                        onChangeSource = onChangeSource,
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
                    // Handled by AnimeScreen — sheet dismisses
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
                text = "Searching installed extensions...",
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
        if (timedOut || failedSources.isNotEmpty()) {
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

@Composable
private fun CandidateRow(
    candidate: StreamResolver.StreamCandidate,
    onClick: () -> Unit,
) {
    val sourceName: String
    val title: String
    val subtitle: String?
    val badge: String

    when (candidate) {
        is StreamResolver.StreamCandidate.Stremio -> {
            sourceName = candidate.addonName
            title = candidate.stream.name?.replace("\n", " ") ?: "Stream"
            subtitle = candidate.stream.title
            badge = if (candidate.cached) "Cached" else "Torrent"
        }
        is StreamResolver.StreamCandidate.Extension -> {
            sourceName = candidate.source.name
            title = candidate.anime.title
            subtitle = null
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
        Icon(
            imageVector = Icons.Outlined.PlayCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = sourceName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
                            text = "Episode ${episode.episodeNumber}",
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
