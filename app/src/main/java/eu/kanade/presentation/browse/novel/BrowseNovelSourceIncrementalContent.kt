package eu.kanade.presentation.browse.novel

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
import androidx.compose.foundation.lazy.grid.items
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
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus

/**
 * Renders incremental search results from a [BrowseNovelSourceScreenModel.incrementalSearchFlow].
 *
 * Unlike [BrowseNovelSourceContent] which uses Paging 3, this composable takes a plain
 * [List]<[Novel]> and renders it directly. As the Flow emits new batches, the list
 * grows and results appear incrementally with a domino-style reveal animation.
 *
 * **Domino animation**: Items reveal one-by-one at a fixed rate (80ms apart), regardless
 * of when batches arrive. A global reveal queue processes items in order — when a new
 * batch arrives, its new items are appended to the queue and revealed sequentially.
 * This creates a continuous left-to-right, top-to-bottom cascade without gaps between
 * batches.
 *
 * @param novels The current cumulative list of novels from the incremental search Flow.
 * @param isLoading Whether the source is still parsing more results.
 * @param columns Grid columns configuration.
 * @param displayMode How to display the novels (grid or list).
 * @param contentPadding Padding from the scaffold.
 * @param onNovelClick Called when a novel is clicked.
 * @param onNovelLongClick Called when a novel is long-clicked.
 */
@Composable
fun BrowseNovelSourceIncrementalContent(
    novels: List<Novel>,
    isLoading: Boolean,
    columns: GridCells,
    displayMode: LibraryDisplayMode,
    contentPadding: PaddingValues,
    onNovelClick: (Novel) -> Unit,
    onNovelLongClick: (Novel) -> Unit,
) {
    // Show loading screen when no results yet and still loading
    if (novels.isEmpty() && isLoading) {
        LoadingScreen(
            modifier = Modifier.padding(contentPadding),
        )
        return
    }

    // Show empty screen when no results and not loading anymore
    if (novels.isEmpty() && !isLoading) {
        EmptyScreen(
            modifier = Modifier.padding(contentPadding),
            message = "No results found",
        )
        return
    }

    // Track which novel IDs have been revealed. Each item becomes visible
    // one-by-one via a global reveal queue. Persist across navigation so the
    // animation doesn't replay when returning from a detail view.
    val savedRevealedIds = rememberSaveable { mutableListOf<Long>() }
    val revealedIds = remember { mutableStateMapOf<Long, Boolean>() }

    // Restore revealed state from saved IDs on first composition
    LaunchedEffect(Unit) {
        for (id in savedRevealedIds) {
            revealedIds[id] = true
        }
    }

    // Adaptive animation speed: when many items arrive at once (e.g., a full
    // page load), use a faster reveal rate (40ms). When items arrive slowly
    // (e.g., incremental search parsing), use the normal rate (80ms).
    // We detect "bulk arrival" by checking if there are many unrevealed items.
    val unrevealedCount = novels.count { !revealedIds.containsKey(it.id) }
    val delayMs = if (unrevealedCount > 10) 40L else 80L

    // Global reveal queue: reveals items one-by-one at a fixed rate.
    // When new novels arrive, only the unseen ones are queued.
    // This ensures a continuous domino effect regardless of batch timing.
    LaunchedEffect(novels) {
        for (novel in novels) {
            if (!revealedIds.containsKey(novel.id)) {
                kotlinx.coroutines.delay(delayMs)
                revealedIds[novel.id] = true
                if (novel.id !in savedRevealedIds) {
                    savedRevealedIds.add(novel.id)
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
            count = novels.size,
            key = { index -> novels[index].id },
        ) { index ->
            val novel = novels[index]
            val isRevealed = revealedIds[novel.id] == true

            // AnimatedVisibility handles the slide + fade transition.
            // Items that aren't revealed yet take up space but are invisible,
            // preventing layout jumps when they appear.
            AnimatedVisibility(
                visible = isRevealed,
                enter = fadeIn(
                    animationSpec = tween(durationMillis = 300),
                ) + slideInVertically(
                    animationSpec = tween(durationMillis = 300),
                    initialOffsetY = { -it / 2 }, // Slide from above (half item height)
                ),
            ) {
                BrowseNovelSourceIncrementalItem(
                    novel = novel,
                    onClick = { onNovelClick(novel) },
                    onLongClick = { onNovelLongClick(novel) },
                )
            }
        }

        // Show loading indicator at the bottom while more results are being parsed
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

@Composable
private fun BrowseNovelSourceIncrementalItem(
    novel: Novel,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = onClick,
) {
    EntryCompactGridItem(
        title = novel.title,
        coverData = NovelCover(
            novelId = novel.id,
            sourceId = novel.source,
            isNovelFavorite = novel.favorite,
            url = novel.thumbnailUrl,
            lastModified = novel.coverLastModified,
        ),
        coverAlpha = if (novel.favorite) CommonEntryItemDefaults.BrowseFavoriteCoverAlpha else 1f,
        coverBadgeStart = {
            InLibraryBadge(enabled = novel.favorite)
        },
        onLongClick = onLongClick,
        onClick = onClick,
    )
}
