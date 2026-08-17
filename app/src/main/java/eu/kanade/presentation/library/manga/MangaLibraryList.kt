package eu.kanade.presentation.library.manga

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import eu.kanade.presentation.library.components.DownloadsBadge
import eu.kanade.presentation.library.components.EntryListItem
import eu.kanade.presentation.library.components.GlobalSearchItem
import eu.kanade.presentation.library.components.LanguageBadge
import eu.kanade.presentation.library.components.ReadingOrderBadge
import eu.kanade.presentation.library.components.UnviewedBadge
import eu.kanade.presentation.library.components.shouldShowContinueViewingAction
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryItem
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.plus

@Composable
internal fun MangaLibraryList(
    items: List<MangaLibraryItem>,
    entries: Int,
    containerHeight: Int,
    contentPadding: PaddingValues,
    selection: List<LibraryManga>,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
    onSeriesClicked: ((Long) -> Unit)? = null,
    onReorder: ((List<Long>) -> Unit)? = null,
    showAuthor: Boolean = false,
    showStatus: Boolean = false,
    getReadingOrderLayer: ((Long) -> Int?)? = null,
    getPreviousLayerMangaIds: (() -> Set<Long>)? = null,
    isEntryLocked: ((Long) -> Boolean)? = null,
) {
    if (onReorder != null) {
        ReorderableMangaLibraryList(
            items = items,
            entries = entries,
            containerHeight = containerHeight,
            contentPadding = contentPadding,
            selection = selection,
            onClick = onClick,
            onLongClick = onLongClick,
            onClickContinueReading = onClickContinueReading,
            searchQuery = searchQuery,
            onGlobalSearchClicked = onGlobalSearchClicked,
            onSeriesClicked = onSeriesClicked,
            onReorder = onReorder,
            getReadingOrderLayer = getReadingOrderLayer,
            getPreviousLayerMangaIds = getPreviousLayerMangaIds,
            isEntryLocked = isEntryLocked,
        )
        return
    }

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
            key = { it.libraryManga.manga.id },
            contentType = { "manga_library_list_item" },
        ) { libraryItem ->
            val manga = libraryItem.libraryManga.manga
            val subtitle = buildListSubtitle(manga, showAuthor, showStatus)
            val isPreviousLayer = getPreviousLayerMangaIds?.invoke()?.contains(manga.id) == true
            EntryListItem(
                isSelected = selection.fastAny { it.id == libraryItem.libraryManga.id },
                coverAlpha = if (isPreviousLayer) 0.4f else 1f,
                title = manga.title,
                coverData = MangaCover(
                    mangaId = manga.id,
                    sourceId = manga.source,
                    isMangaFavorite = manga.favorite,
                    url = manga.thumbnailUrl,
                    lastModified = manga.coverLastModified,
                ),
                badge = {
                    DownloadsBadge(count = libraryItem.downloadCount)
                    UnviewedBadge(count = libraryItem.unreadCount)
                    LanguageBadge(
                        isLocal = libraryItem.isLocal,
                        sourceLanguage = libraryItem.sourceLanguage,
                    )
                    val roLayer = getReadingOrderLayer?.invoke(manga.id)
                    if (roLayer != null) {
                        ReadingOrderBadge(layer = roLayer)
                    }
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
                entries = entries,
                containerHeight = containerHeight,
                subtitle = subtitle,
            )
        }
    }
}

@Composable
private fun ReorderableMangaLibraryList(
    items: List<MangaLibraryItem>,
    entries: Int,
    containerHeight: Int,
    contentPadding: PaddingValues,
    selection: List<LibraryManga>,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
    onSeriesClicked: ((Long) -> Unit)?,
    onReorder: (List<Long>) -> Unit,
    showAuthor: Boolean = false,
    showStatus: Boolean = false,
    getReadingOrderLayer: ((Long) -> Int?)? = null,
    getPreviousLayerMangaIds: (() -> Set<Long>)? = null,
    isEntryLocked: ((Long) -> Boolean)? = null,
) {
    val listState = remember { androidx.compose.foundation.lazy.LazyListState() }
    val itemState = remember { items.toMutableStateList() }

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val item = itemState.removeAt(from.index)
        itemState.add(to.index, item)
        onReorder(itemState.map { it.libraryManga.manga.id })
    }

    LaunchedEffect(items) {
        if (!reorderableState.isAnyItemDragging) {
            itemState.clear()
            itemState.addAll(items)
        }
    }

    FastScrollLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
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
            items = itemState,
            key = { it.libraryManga.manga.id },
            contentType = { "manga_library_list_item" },
        ) { libraryItem ->
            ReorderableItem(reorderableState, libraryItem.libraryManga.manga.id) {
                val manga = libraryItem.libraryManga.manga
                val subtitle = buildListSubtitle(manga, showAuthor, showStatus)
                val isPreviousLayer = getPreviousLayerMangaIds?.invoke()?.contains(manga.id) == true
                EntryListItem(
                    modifier = Modifier.longPressDraggableHandle(),
                    isSelected = selection.fastAny { it.id == libraryItem.libraryManga.id },
                    coverAlpha = if (isPreviousLayer) 0.4f else 1f,
                    title = manga.title,
                    subtitle = subtitle,
                    coverData = MangaCover(
                        mangaId = manga.id,
                        sourceId = manga.source,
                        isMangaFavorite = manga.favorite,
                        url = manga.thumbnailUrl,
                        lastModified = manga.coverLastModified,
                    ),
                    badge = {
                        DownloadsBadge(count = libraryItem.downloadCount)
                        UnviewedBadge(count = libraryItem.unreadCount)
                        LanguageBadge(
                            isLocal = libraryItem.isLocal,
                            sourceLanguage = libraryItem.sourceLanguage,
                        )
                        val roLayer = getReadingOrderLayer?.invoke(manga.id)
                        if (roLayer != null) {
                            ReadingOrderBadge(layer = roLayer)
                        }
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
                    entries = entries,
                    containerHeight = containerHeight,
                )
            }
        }
    }
}

@Composable
internal fun buildListSubtitle(
    manga: tachiyomi.domain.entries.manga.model.Manga,
    showAuthor: Boolean,
    showStatus: Boolean,
): String? {
    val parts = buildList {
        if (showAuthor) {
            val authorArtist = if (manga.author == manga.artist || manga.artist.isNullOrBlank()) {
                manga.author?.trim()?.takeIf { it.isNotBlank() }
            } else {
                listOfNotNull(
                    manga.author?.trim()?.takeIf { it.isNotBlank() },
                    manga.artist?.trim()?.takeIf { it.isNotBlank() },
                ).joinToString(", ").takeIf { it.isNotBlank() }
            }
            if (!authorArtist.isNullOrBlank()) add(authorArtist)
        }
        if (showStatus) {
            val statusStr = when (manga.status.toInt()) {
                eu.kanade.tachiyomi.source.model.SManga.ONGOING -> stringResource(tachiyomi.i18n.MR.strings.ongoing)
                eu.kanade.tachiyomi.source.model.SManga.COMPLETED -> stringResource(tachiyomi.i18n.MR.strings.completed)
                eu.kanade.tachiyomi.source.model.SManga.LICENSED -> stringResource(tachiyomi.i18n.MR.strings.licensed)
                eu.kanade.tachiyomi.source.model.SManga.PUBLISHING_FINISHED -> stringResource(tachiyomi.i18n.MR.strings.publishing_finished)
                eu.kanade.tachiyomi.source.model.SManga.CANCELLED -> stringResource(tachiyomi.i18n.MR.strings.cancelled)
                eu.kanade.tachiyomi.source.model.SManga.ON_HIATUS -> stringResource(tachiyomi.i18n.MR.strings.on_hiatus)
                else -> null
            }
            if (!statusStr.isNullOrBlank()) add(statusStr)
        }
    }
    return if (parts.isEmpty()) null else parts.joinToString(" • ")
}
