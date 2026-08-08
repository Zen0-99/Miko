package eu.kanade.presentation.browse.anime

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.browse.InLibraryBadge
import eu.kanade.presentation.library.components.CommonEntryItemDefaults
import eu.kanade.presentation.library.components.EntryCompactGridItem
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus

/**
 * Renders incremental browse results with a domino-style reveal animation.
 *
 * See [BrowseNovelSourceIncrementalContent] for full documentation — this is
 * the anime equivalent.
 */
@Composable
fun BrowseAnimeSourceIncrementalContent(
    animeList: List<Anime>,
    isLoading: Boolean,
    columns: GridCells,
    displayMode: LibraryDisplayMode,
    contentPadding: PaddingValues,
    onAnimeClick: (Anime) -> Unit,
    onAnimeLongClick: (Anime) -> Unit,
) {
    if (animeList.isEmpty() && isLoading) {
        LoadingScreen(
            modifier = Modifier.padding(contentPadding),
        )
        return
    }

    if (animeList.isEmpty() && !isLoading) {
        EmptyScreen(
            modifier = Modifier.padding(contentPadding),
            message = "No results found",
        )
        return
    }

    val savedRevealedIds = rememberSaveable { mutableListOf<Long>() }
    val revealedIds = remember { mutableStateMapOf<Long, Boolean>() }

    LaunchedEffect(Unit) {
        for (id in savedRevealedIds) {
            revealedIds[id] = true
        }
    }

    val unrevealedCount = animeList.count { !revealedIds.containsKey(it.id) }
    val delayMs = if (unrevealedCount > 10) 40L else 80L

    LaunchedEffect(animeList) {
        for (anime in animeList) {
            if (!revealedIds.containsKey(anime.id)) {
                kotlinx.coroutines.delay(delayMs)
                revealedIds[anime.id] = true
                if (anime.id !in savedRevealedIds) {
                    savedRevealedIds.add(anime.id)
                }
            }
        }
    }

    LazyVerticalGrid(
        columns = columns,
        contentPadding = contentPadding + PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(CommonEntryItemDefaults.GridVerticalSpacer),
        horizontalArrangement = Arrangement.spacedBy(CommonEntryItemDefaults.GridHorizontalSpacer),
    ) {
        items(
            count = animeList.size,
            key = { index -> animeList[index].id },
        ) { index ->
            val anime = animeList[index]
            val isRevealed = revealedIds[anime.id] == true

            AnimatedVisibility(
                visible = isRevealed,
                enter = fadeIn(
                    animationSpec = tween(durationMillis = 300),
                ) + slideInVertically(
                    animationSpec = tween(durationMillis = 300),
                    initialOffsetY = { -it / 2 },
                ),
            ) {
                EntryCompactGridItem(
                    title = anime.title,
                    coverData = AnimeCover(
                        animeId = anime.id,
                        sourceId = anime.source,
                        isAnimeFavorite = anime.favorite,
                        url = anime.thumbnailUrl,
                        lastModified = anime.coverLastModified,
                    ),
                    coverAlpha = if (anime.favorite) CommonEntryItemDefaults.BrowseFavoriteCoverAlpha else 1f,
                    coverBadgeStart = {
                        InLibraryBadge(enabled = anime.favorite)
                    },
                    onLongClick = { onAnimeLongClick(anime) },
                    onClick = { onAnimeClick(anime) },
                )
            }
        }

        if (isLoading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(8.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}
