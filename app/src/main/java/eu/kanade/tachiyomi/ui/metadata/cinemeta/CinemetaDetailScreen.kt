package eu.kanade.tachiyomi.ui.metadata.cinemeta

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import coil3.compose.AsyncImage
import eu.kanade.presentation.util.Screen

data class CinemetaDetailScreen(
    val type: String,
    val id: String,
) : Screen() {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { CinemetaDetailScreenModel(type, id) }
        val state by screenModel.state.collectAsState()

        when {
            state.loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.error != null -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = state.error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = "Retry",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable { screenModel.retry() },
                    )
                }
            }

            state.meta != null -> {
                DetailContent(
                    meta = state.meta!!,
                    episodesBySeason = screenModel.episodesBySeason,
                )
            }
        }
    }

    @Composable
    private fun DetailContent(
        meta: eu.kanade.tachiyomi.metadata.miko.dto.MikoMeta,
        episodesBySeason: Map<Int, List<eu.kanade.tachiyomi.metadata.miko.dto.MikoEpisode>>,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
        ) {
            // Backdrop
            item {
                meta.background?.let { backdrop ->
                    AsyncImage(
                        model = backdrop,
                        contentScale = ContentScale.Crop,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                    )
                }
            }

            // Poster + title
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    meta.poster?.let { poster ->
                        AsyncImage(
                            model = poster,
                            contentScale = ContentScale.Crop,
                            contentDescription = meta.name,
                            modifier = Modifier
                                .width(100.dp)
                                .height(150.dp)
                                .clip(MaterialTheme.shapes.small),
                        )
                    }
                    Column {
                        Text(
                            text = meta.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        meta.releaseInfo?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Metadata badges
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    meta.imdbRating?.let {
                        Text("★ $it", style = MaterialTheme.typography.labelMedium)
                    }
                    meta.runtime?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    meta.year?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    meta.country?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Genres
            meta.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                item {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        genres.forEach { genre ->
                            AssistChip(onClick = {}, label = { Text(genre, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                }
            }

            // Description
            meta.description?.let { desc ->
                item {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            // Cast
            meta.cast?.takeIf { it.isNotEmpty() }?.let { cast ->
                item {
                    Text(
                        text = "Cast: ${cast.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            // Director
            meta.director?.takeIf { it.isNotEmpty() }?.let { director ->
                item {
                    Text(
                        text = "Director: ${director.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            // Awards
            meta.awards?.let { awards ->
                item {
                    Text(
                        text = awards,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            // Episodes (series only)
            if (meta.type == "series" && episodesBySeason.isNotEmpty()) {
                episodesBySeason.forEach { (season, episodes) ->
                    item {
                        Text(
                            text = if (season == 0) "Specials" else "Season $season",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(episodes.sortedBy { it.number }) { episode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            episode.thumbnail?.let { thumb ->
                                AsyncImage(
                                    model = thumb,
                                    contentScale = ContentScale.Crop,
                                    contentDescription = episode.name,
                                    modifier = Modifier
                                        .width(96.dp)
                                        .height(54.dp)
                                        .clip(MaterialTheme.shapes.small),
                                )
                            }
                            Column {
                                Text(
                                    text = "E${episode.number} - ${episode.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                episode.overview?.let { overview ->
                                    Text(
                                        text = overview,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
