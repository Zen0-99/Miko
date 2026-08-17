package eu.kanade.presentation.library.novel

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import eu.kanade.presentation.library.components.DownloadsBadge
import eu.kanade.presentation.library.components.EntryListItem
import eu.kanade.presentation.library.components.GlobalSearchItem
import eu.kanade.presentation.library.components.LanguageBadge
import eu.kanade.presentation.library.components.ReadingOrderBadge
import eu.kanade.presentation.library.components.UnviewedBadge
import eu.kanade.tachiyomi.ui.library.novel.NovelLibraryItem
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.util.plus

@Composable
internal fun NovelLibraryList(
    items: List<NovelLibraryItem>,
    entries: Int,
    containerHeight: Int,
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
    FastScrollLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(vertical = 8.dp),
    ) {
        item(key = "global_search") {
            if (!searchQuery.isNullOrEmpty()) {
                GlobalSearchItem(
                    modifier = Modifier.fillMaxWidth(),
                    searchQuery = searchQuery,
                    onClick = onGlobalSearchClicked,
                )
            }
        }

        items(
            items = items,
            key = { it.libraryNovel.novel.id },
            contentType = { "novel_library_list_item" },
        ) { libraryItem ->
            val novel = libraryItem.libraryNovel.novel
            val isPreviousLayer = getPreviousLayerNovelIds?.invoke()?.contains(novel.id) == true
            EntryListItem(
                isSelected = selection.fastAny { it.id == libraryItem.libraryNovel.id },
                coverAlpha = if (isPreviousLayer) 0.4f else 1f,
                title = novel.title,
                coverData = NovelCover(
                    novelId = novel.id,
                    sourceId = novel.source,
                    isNovelFavorite = novel.favorite,
                    url = novel.thumbnailUrl,
                    lastModified = novel.coverLastModified,
                ),
                badge = {
                    DownloadsBadge(count = libraryItem.downloadCount)
                    UnviewedBadge(count = libraryItem.unreadCount)
                    LanguageBadge(
                        isLocal = libraryItem.isLocal,
                        sourceLanguage = libraryItem.sourceLanguage,
                    )
                    val roLayer = getReadingOrderLayer?.invoke(novel.id)
                    if (roLayer != null) {
                        ReadingOrderBadge(layer = roLayer)
                    }
                },
                onLongClick = { onLongClick(libraryItem.libraryNovel) },
                onClick = { onClick(libraryItem.libraryNovel) },
                onClickContinueViewing = if (onClickContinueReading != null && libraryItem.unreadCount > 0 && isEntryLocked?.invoke(novel.id) != true) {
                    { onClickContinueReading(libraryItem.libraryNovel) }
                } else {
                    null
                },
                entries = entries,
                containerHeight = containerHeight,
            )
        }
    }
}
