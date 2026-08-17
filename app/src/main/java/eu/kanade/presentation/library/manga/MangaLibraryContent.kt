package eu.kanade.presentation.library.manga

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.presentation.library.components.LibraryTabs
import eu.kanade.presentation.library.components.CollectionHeaderRow
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.library.model.LibraryCollectionDisplay
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.presentation.core.components.material.PullRefresh
import kotlin.time.Duration.Companion.seconds

@Composable
fun MangaLibraryContent(
    collections: List<Collection>,
    searchQuery: String?,
    selection: List<LibraryManga>,
    contentPadding: PaddingValues,
    currentPage: () -> Int,
    hasActiveFilters: Boolean,
    showPageTabs: Boolean,
    onChangeCurrentPage: (Int) -> Unit,
    onMangaClicked: (Long) -> Unit,
    onContinueReadingClicked: ((LibraryManga) -> Unit)?,
    onToggleSelection: (LibraryManga) -> Unit,
    onToggleRangeSelection: (LibraryManga) -> Unit,
    onRefresh: (Collection?) -> Boolean,
    onGlobalSearchClicked: () -> Unit,
    getNumberOfMangaForCollection: (Collection) -> Int?,
    getDisplayMode: (Int) -> PreferenceMutableState<LibraryDisplayMode>,
    getColumnsForOrientation: (Boolean) -> PreferenceMutableState<Int>,
    collectionDisplayMode: LibraryCollectionDisplay = LibraryCollectionDisplay.TABBED,
    getLibraryForPage: (Int) -> List<MangaLibraryItem>,
    onTogglePinned: ((MangaLibraryItem) -> Unit)? = null,
    onSeriesClicked: ((Long) -> Unit)? = null,
    onReorder: ((List<Long>) -> Unit)? = null,
    sortLabel: String? = null,
    sortDescending: Boolean? = null,
    onSortClick: () -> Unit = {},
    showLibraryTitle: Boolean = true,
    showListAuthor: Boolean = false,
    showListStatus: Boolean = false,
    getSortLabelForCollection: (Collection) -> String? = { null },
    getSortDescendingForCollection: (Collection) -> Boolean? = { null },
    onSortClickForCollection: (Collection) -> Unit = {},
    getReadingOrderLayer: ((Long) -> Int?)? = null,
    readingOrderMode: Boolean = false,
    getPreviousLayerMangaIds: (() -> Set<Long>)? = null,
    isEntryLocked: ((Long) -> Boolean)? = null,
) {
    if (collectionDisplayMode == LibraryCollectionDisplay.CONTINUOUS && collections.size > 1) {
        MangaLibraryContinuousContent(
            collections = collections,
            searchQuery = searchQuery,
            selection = selection,
            contentPadding = contentPadding,
            hasActiveFilters = hasActiveFilters,
            onMangaClicked = onMangaClicked,
            onContinueReadingClicked = onContinueReadingClicked,
            onToggleSelection = onToggleSelection,
            onToggleRangeSelection = onToggleRangeSelection,
            onRefresh = onRefresh,
            onGlobalSearchClicked = onGlobalSearchClicked,
            getNumberOfMangaForCollection = getNumberOfMangaForCollection,
            getDisplayMode = getDisplayMode,
            getColumnsForOrientation = getColumnsForOrientation,
            getLibraryForPage = getLibraryForPage,
            sortLabel = sortLabel,
            sortDescending = sortDescending,
            onSortClick = onSortClick,
            getSortLabelForCollection = getSortLabelForCollection,
            getSortDescendingForCollection = getSortDescendingForCollection,
            onSortClickForCollection = onSortClickForCollection,
            showListAuthor = showListAuthor,
            showListStatus = showListStatus,
            isEntryLocked = isEntryLocked,
        )
        return
    }

    Column(
        modifier = Modifier.padding(
            top = contentPadding.calculateTopPadding(),
            start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
            end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
        ),
    ) {
        val coercedCurrentPage = remember { currentPage().coerceAtMost(collections.lastIndex) }
        val pagerState = rememberPagerState(coercedCurrentPage) { collections.size }

        val scope = rememberCoroutineScope()
        var isRefreshing by remember(pagerState.currentPage) { mutableStateOf(false) }

        if (showPageTabs && collections.size > 1) {
            LaunchedEffect(collections) {
                if (collections.size <= pagerState.currentPage) {
                    pagerState.scrollToPage(collections.size - 1)
                }
            }
            LibraryTabs(
                collections = collections,
                pagerState = pagerState,
                getNumberOfItemsForCollection = getNumberOfMangaForCollection,
                onTabItemClick = { scope.launch { pagerState.animateScrollToPage(it) } },
                sortLabel = sortLabel,
                sortDescending = sortDescending,
                onSortClick = onSortClick,
            )
        } else {
            // When tabs are off, show a collection header (like continuous mode)
            // Sort button is always shown; title is controlled by showLibraryTitle
            val currentCollection = collections.getOrNull(pagerState.currentPage)
            if (currentCollection != null) {
                CollectionHeaderRow(
                    title = currentCollection.let { if (it.name.isBlank()) "Default" else it.name },
                    itemCount = getNumberOfMangaForCollection(currentCollection),
                    sortLabel = sortLabel,
                    sortDescending = sortDescending,
                    onSortClick = onSortClick,
                    showTitle = showLibraryTitle,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        val notSelectionMode = selection.isEmpty() && !readingOrderMode
        val onClickManga = { manga: LibraryManga ->
            if (notSelectionMode) {
                onMangaClicked(manga.manga.id)
            } else {
                onToggleSelection(manga)
            }
        }

        PullRefresh(
            refreshing = isRefreshing,
            onRefresh = {
                val started = onRefresh(collections[currentPage()])
                if (!started) return@PullRefresh
                scope.launch {
                    // Fake refresh status but hide it after a second as it's a long running task
                    isRefreshing = true
                    delay(1.seconds)
                    isRefreshing = false
                }
            },
            enabled = notSelectionMode,
        ) {
            MangaLibraryPager(
                state = pagerState,
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                hasActiveFilters = hasActiveFilters,
                selectedManga = selection,
                searchQuery = searchQuery,
                onGlobalSearchClicked = onGlobalSearchClicked,
                getDisplayMode = getDisplayMode,
                getColumnsForOrientation = getColumnsForOrientation,
                getLibraryForPage = getLibraryForPage,
                onClickManga = onClickManga,
                onLongClickManga = onToggleRangeSelection,
                onClickContinueReading = onContinueReadingClicked,
                onTogglePinned = onTogglePinned,
                onSeriesClicked = onSeriesClicked,
                onReorder = onReorder,
                showListAuthor = showListAuthor,
                showListStatus = showListStatus,
                getReadingOrderLayer = getReadingOrderLayer,
                getPreviousLayerMangaIds = getPreviousLayerMangaIds,
                isEntryLocked = isEntryLocked,
            )
        }

        LaunchedEffect(pagerState.currentPage) {
            onChangeCurrentPage(pagerState.currentPage)
        }
    }
}
