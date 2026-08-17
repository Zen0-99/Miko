package eu.kanade.presentation.library.novel

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastAny
import eu.kanade.presentation.library.components.DownloadsBadge
import eu.kanade.presentation.library.components.EntryCompactGridItem
import eu.kanade.presentation.library.components.LanguageBadge
import eu.kanade.presentation.library.components.LazyLibraryGrid
import eu.kanade.presentation.library.components.ReadingOrderBadge
import eu.kanade.presentation.library.components.UnviewedBadge
import eu.kanade.presentation.library.components.globalSearchItem
import eu.kanade.tachiyomi.ui.library.novel.NovelLibraryItem
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.library.novel.LibraryNovel

@Composable
internal fun NovelLibraryCompactGrid(
    items: List<NovelLibraryItem>,
    showTitle: Boolean,
    columns: Int,
    contentPadding: PaddingValues,
    selection: List<LibraryNovel>,
    onClick: (LibraryNovel) -> Unit,
    onLongClick: (LibraryNovel) -> Unit,
    onClickContinueReading: ((LibraryNovel) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
    getReadingOrderLayer: ((Long) -> Int?)? = null,
    getPreviousLayerNovelIds: (() -> Set<Long>)? = null,
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
            key = { it.libraryNovel.novel.id },
            contentType = { "novel_library_compact_grid_item" },
        ) { libraryItem ->
            val novel = libraryItem.libraryNovel.novel
            val isPreviousLayer = getPreviousLayerNovelIds?.invoke()?.contains(novel.id) == true
            EntryCompactGridItem(
                isSelected = selection.fastAny { it.id == libraryItem.libraryNovel.id },
                coverAlpha = if (isPreviousLayer) 0.4f else 1f,
                title = novel.title.takeIf { showTitle },
                coverData = NovelCover(
                    novelId = novel.id,
                    sourceId = novel.source,
                    isNovelFavorite = novel.favorite,
                    url = novel.thumbnailUrl,
                    lastModified = novel.coverLastModified,
                ),
                coverBadgeStart = {
                    DownloadsBadge(count = libraryItem.downloadCount)
                    UnviewedBadge(count = libraryItem.unreadCount)
                },
                coverBadgeEnd = {
                    val roLayer = getReadingOrderLayer?.invoke(novel.id)
                    if (roLayer != null) {
                        ReadingOrderBadge(layer = roLayer)
                    }
                    LanguageBadge(
                        isLocal = libraryItem.isLocal,
                        sourceLanguage = libraryItem.sourceLanguage,
                    )
                },
                onLongClick = { onLongClick(libraryItem.libraryNovel) },
                onClick = { onClick(libraryItem.libraryNovel) },
                onClickContinueViewing = if (onClickContinueReading != null && libraryItem.unreadCount > 0 && isEntryLocked?.invoke(novel.id) != true) {
                    { onClickContinueReading(libraryItem.libraryNovel) }
                } else {
                    null
                },
            )
        }
    }
}
