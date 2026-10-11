package miko.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import miko.ui.reader.ChapterListSheet
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Episode player surface: the shared [VideoPlayer] renders the selected
 * stream with native controls; the resolved stream list below it acts as
 * Android's quality-source selection (first quality autoplays).
 */
@Composable
fun EpisodePlayerScreen(
    model: EpisodePlayerScreenModel,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        state.playingUrl?.let { url ->
            VideoPlayer(
                url = url,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
            )
        }
        when {
            state.loading -> LoadingScreen(modifier = Modifier.weight(1f))
            state.error != null -> Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = state.error ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = { model.openEpisode(state.episodeUrl) }) {
                    Text(stringResource(MR.strings.action_retry))
                }
            }
            state.videos.isEmpty() -> EmptyScreen(
                stringRes = MR.strings.no_results_found,
                modifier = Modifier.weight(1f),
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                item {
                    Text(
                        text = state.episodeName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(state.videos, key = { it.url }) { video ->
                    val playing = video.url == state.playingUrl
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { model.playVideo(video.url) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            tint = if (playing) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = video.title.ifBlank { video.url },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (playing) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }

        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = model::prevEpisode,
                enabled = state.hasPrev,
            ) {
                Text(stringResource(MR.strings.action_previous_chapter))
            }
            IconButton(onClick = model::openEpisodeSheet) {
                Icon(
                    Icons.AutoMirrored.Filled.FormatListBulleted,
                    contentDescription = stringResource(MR.strings.chapters),
                )
            }
            TextButton(
                onClick = model::nextEpisode,
                enabled = state.hasNext,
            ) {
                Text(stringResource(MR.strings.action_next_chapter))
            }
        }
    }

    if (state.showEpisodes) {
        ChapterListSheet(
            chapters = state.episodes,
            currentUrl = state.episodeUrl,
            onDismiss = model::closeEpisodeSheet,
            onSelect = model::openEpisode,
        )
    }
}
