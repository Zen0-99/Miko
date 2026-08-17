package eu.kanade.presentation.library.anime

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastAny
import eu.kanade.presentation.library.components.DownloadsBadge
import eu.kanade.presentation.library.components.EntryComfortableGridItem
import eu.kanade.presentation.library.components.LanguageBadge
import eu.kanade.presentation.library.components.LazyLibraryGrid
import eu.kanade.presentation.library.components.ReadingOrderBadge
import eu.kanade.presentation.library.components.UnviewedBadge
import eu.kanade.presentation.library.components.globalSearchItem
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryItem
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.library.anime.LibraryAnime

@Composable
internal fun AnimeLibraryComfortableGrid(
    items: List<AnimeLibraryItem>,
    columns: Int,
    contentPadding: PaddingValues,
    selection: List<LibraryAnime>,
    onClick: (LibraryAnime) -> Unit,
    onLongClick: (LibraryAnime) -> Unit,
    onClickContinueWatching: ((LibraryAnime) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
    getReadingOrderLayer: ((Long) -> Int?)? = null,
    getPreviousLayerAnimeIds: (() -> Set<Long>)? = null,
    isEntryLocked: ((Long) -> Boolean)? = null,
) {
    LazyLibraryGrid(
        modifier = Modifier.fillMaxSize(),
        columns = columns,
        contentPadding = contentPadding,
    ) {
        globalSearchItem(searchQuery, onGlobalSearchClicked)

        items(
            items = items,
            key = { it.libraryAnime.anime.id },
            contentType = { "anime_library_comfortable_grid_item" },
        ) { libraryItem ->
            val anime = libraryItem.libraryAnime.anime
            val isPreviousLayer = getPreviousLayerAnimeIds?.invoke()?.contains(anime.id) == true
            EntryComfortableGridItem(
                isSelected = selection.fastAny { it.id == libraryItem.libraryAnime.id },
                coverAlpha = if (isPreviousLayer) 0.4f else 1f,
                title = anime.title,
                coverData = AnimeCover(
                    animeId = anime.id,
                    sourceId = anime.source,
                    isAnimeFavorite = anime.favorite,
                    url = anime.thumbnailUrl,
                    lastModified = anime.coverLastModified,
                ),
                coverBadgeStart = {
                    DownloadsBadge(count = libraryItem.downloadCount)
                    UnviewedBadge(count = libraryItem.unseenCount)
                },
                coverBadgeEnd = {
                    val roLayer = getReadingOrderLayer?.invoke(anime.id)
                    if (roLayer != null) {
                        ReadingOrderBadge(layer = roLayer)
                    }
                    LanguageBadge(
                        isLocal = libraryItem.isLocal,
                        sourceLanguage = libraryItem.sourceLanguage,
                    )
                },
                onLongClick = { onLongClick(libraryItem.libraryAnime) },
                onClick = { onClick(libraryItem.libraryAnime) },
                onClickContinueViewing = if (onClickContinueWatching != null && libraryItem.unseenCount > 0 && isEntryLocked?.invoke(anime.id) != true) {
                    { onClickContinueWatching(libraryItem.libraryAnime) }
                } else {
                    null
                },
            )
        }
    }
}
