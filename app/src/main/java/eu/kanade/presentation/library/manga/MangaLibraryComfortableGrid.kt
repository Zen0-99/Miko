package eu.kanade.presentation.library.manga

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
import eu.kanade.presentation.library.components.PinnedBadge
import eu.kanade.presentation.library.components.ReadingOrderBadge
import eu.kanade.presentation.library.components.UnviewedBadge
import eu.kanade.presentation.library.components.globalSearchItem
import eu.kanade.presentation.library.components.shouldShowContinueViewingAction
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryItem
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.library.manga.LibraryManga

@Composable
internal fun MangaLibraryComfortableGrid(
    items: List<MangaLibraryItem>,
    columns: Int,
    contentPadding: PaddingValues,
    selection: List<LibraryManga>,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
    onTogglePinned: ((MangaLibraryItem) -> Unit)? = null,
    onSeriesClicked: ((Long) -> Unit)? = null,
    performanceMode: Boolean = false,
    showAuthor: Boolean = false,
    getReadingOrderLayer: ((Long) -> Int?)? = null,
    getPreviousLayerMangaIds: (() -> Set<Long>)? = null,
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
            key = { it.libraryManga.manga.id },
            contentType = { "manga_library_comfortable_grid_item" },
        ) { libraryItem ->
            val manga = libraryItem.libraryManga.manga
            val isPreviousLayer = getPreviousLayerMangaIds?.invoke()?.contains(manga.id) == true
            val authorSubtitle = if (showAuthor) {
                val authorArtist = if (manga.author == manga.artist || manga.artist.isNullOrBlank()) {
                    manga.author?.trim()?.takeIf { it.isNotBlank() }
                } else {
                    listOfNotNull(
                        manga.author?.trim()?.takeIf { it.isNotBlank() },
                        manga.artist?.trim()?.takeIf { it.isNotBlank() },
                    ).joinToString(", ").takeIf { it.isNotBlank() }
                }
                authorArtist
            } else null
            EntryComfortableGridItem(
                isSelected = selection.fastAny { it.id == libraryItem.libraryManga.id },
                coverAlpha = if (isPreviousLayer) 0.4f else 1f,
                title = manga.title,
                subtitle = authorSubtitle,
                coverData = MangaCover(
                    mangaId = manga.id,
                    sourceId = manga.source,
                    isMangaFavorite = manga.favorite,
                    url = manga.thumbnailUrl,
                    lastModified = manga.coverLastModified,
                ),
                coverBadgeStart = {
                    DownloadsBadge(count = libraryItem.downloadCount)
                    UnviewedBadge(count = libraryItem.unreadCount)
                },
                coverBadgeEnd = {
                    val roLayer = getReadingOrderLayer?.invoke(manga.id)
                    if (roLayer != null) {
                        ReadingOrderBadge(layer = roLayer)
                    }
                    LanguageBadge(
                        isLocal = libraryItem.isLocal,
                        sourceLanguage = libraryItem.sourceLanguage,
                    )
                },
                topEndBadge = if (libraryItem.pinned) {
                    { PinnedBadge() }
                } else {
                    null
                },
                onLongClick = { onLongClick(libraryItem.libraryManga) },
                onClick = { onClick(libraryItem.libraryManga) },
                onClickContinueViewing = if (
                    shouldShowContinueViewingAction(
                        hasContinueAction = onClickContinueReading != null &&
                            isEntryLocked?.invoke(manga.id) != true,
                        remainingCount = libraryItem.unreadCount,
                    )
                ) {
                    { onClickContinueReading?.invoke(libraryItem.libraryManga) }
                } else {
                    null
                },
            )
        }
    }
}
