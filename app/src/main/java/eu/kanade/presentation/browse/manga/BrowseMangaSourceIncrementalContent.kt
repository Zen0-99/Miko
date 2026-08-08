package eu.kanade.presentation.browse.manga

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
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus

/**
 * Renders incremental browse results with a domino-style reveal animation.
 *
 * See [BrowseNovelSourceIncrementalContent] for full documentation — this is
 * the manga equivalent.
 */
@Composable
fun BrowseMangaSourceIncrementalContent(
    mangaList: List<Manga>,
    isLoading: Boolean,
    columns: GridCells,
    displayMode: LibraryDisplayMode,
    contentPadding: PaddingValues,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
) {
    if (mangaList.isEmpty() && isLoading) {
        LoadingScreen(
            modifier = Modifier.padding(contentPadding),
        )
        return
    }

    if (mangaList.isEmpty() && !isLoading) {
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

    val unrevealedCount = mangaList.count { !revealedIds.containsKey(it.id) }
    val delayMs = if (unrevealedCount > 10) 40L else 80L

    LaunchedEffect(mangaList) {
        for (manga in mangaList) {
            if (!revealedIds.containsKey(manga.id)) {
                kotlinx.coroutines.delay(delayMs)
                revealedIds[manga.id] = true
                if (manga.id !in savedRevealedIds) {
                    savedRevealedIds.add(manga.id)
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
            count = mangaList.size,
            key = { index -> mangaList[index].id },
        ) { index ->
            val manga = mangaList[index]
            val isRevealed = revealedIds[manga.id] == true

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
                    title = manga.title,
                    coverData = MangaCover(
                        mangaId = manga.id,
                        sourceId = manga.source,
                        isMangaFavorite = manga.favorite,
                        url = manga.thumbnailUrl,
                        lastModified = manga.coverLastModified,
                    ),
                    coverAlpha = if (manga.favorite) CommonEntryItemDefaults.BrowseFavoriteCoverAlpha else 1f,
                    coverBadgeStart = {
                        InLibraryBadge(enabled = manga.favorite)
                    },
                    onLongClick = { onMangaLongClick(manga) },
                    onClick = { onMangaClick(manga) },
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
