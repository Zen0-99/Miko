package miko.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun NovelDetailsScreen(
    model: NovelDetailsScreenModel,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            NovelHeader(
                coverUrl = state.novel?.thumbnail_url,
                title = state.novel?.title ?: state.local?.title ?: "",
                author = state.novel?.author,
                status = state.novel?.status,
            )
        }

        item {
            Button(
                onClick = model::toggleFavorite,
                enabled = state.novel != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    stringResource(
                        if (state.favorite) MR.strings.in_library else MR.strings.add_to_library,
                    ),
                )
            }
        }

        state.novel?.description?.let { description ->
            item {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        state.novel?.getGenres()?.let { genres ->
            if (genres.isNotEmpty()) {
                item {
                    Text(
                        text = genres.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }

        item {
            Text(
                text = stringResource(MR.strings.chapters),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        when {
            state.chaptersLoading -> item { LoadingScreen() }
            state.chapters.isEmpty() -> item {
                Text(
                    text = stringResource(MR.strings.no_results_found),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            else -> {
                val chapters = state.chapters
                items(chapters, key = { it.url }) { chapter ->
                    ChapterRow(chapter = chapter)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun NovelHeader(
    coverUrl: String?,
    title: String,
    author: String?,
    status: Int?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AsyncImage(
            model = coverUrl,
            contentDescription = title,
            modifier = Modifier
                .width(96.dp)
                .aspectRatio(0.7f),
            contentScale = ContentScale.Crop,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            author?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            status?.let {
                Text(
                    text = statusLabel(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChapterRow(chapter: SNovelChapter) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text = chapter.name, style = MaterialTheme.typography.bodyLarge)
        if (chapter.chapter_number >= 0f) {
            Text(
                text = "Ch. ${chapter.chapter_number}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun statusLabel(status: Int): String = when (status) {
    SNovel.ONGOING -> "Ongoing"
    SNovel.COMPLETED -> "Completed"
    SNovel.LICENSED -> "Licensed"
    SNovel.PUBLISHING_FINISHED -> "Publishing finished"
    SNovel.CANCELLED -> "Cancelled"
    SNovel.ON_HIATUS -> "On hiatus"
    else -> "Unknown"
}
