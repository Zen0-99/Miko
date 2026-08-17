package eu.kanade.presentation.library.novel

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
import eu.kanade.tachiyomi.ui.library.novel.NovelLibraryItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.presentation.core.components.material.PullRefresh
import kotlin.time.Duration.Companion.seconds

@Composable
fun NovelLibraryContent(
    collections: List<Collection>,
    searchQuery: String?,
    selection: List<LibraryNovel>,
    contentPadding: PaddingValues,
    currentPage: () -> Int,
    hasActiveFilters: Boolean,
    showPageTabs: Boolean,
    onChangeCurrentPage: (Int) -> Unit,
    onNovelClicked: (Long) -> Unit,
    onContinueReadingClicked: ((LibraryNovel) -> Unit)?,
    onToggleSelection: (LibraryNovel) -> Unit,
    onToggleRangeSelection: (LibraryNovel) -> Unit,
    onRefresh: (Collection?) -> Boolean,
    onGlobalSearchClicked: () -> Unit,
    getNumberOfNovelsForCollection: (Collection) -> Int?,
    getDisplayMode: (Int) -> PreferenceMutableState<LibraryDisplayMode>,
    getColumnsForOrientation: (Boolean) -> PreferenceMutableState<Int>,
    getLibraryForPage: (Int) -> List<NovelLibraryItem>,
    sortLabel: String? = null,
    sortDescending: Boolean? = null,
    onSortClick: () -> Unit = {},
    showLibraryTitle: Boolean = true,
    getReadingOrderLayer: ((Long) -> Int?)? = null,
    readingOrderMode: Boolean = false,
    getPreviousLayerNovelIds: (() -> Set<Long>)? = null,
    isEntryLocked: ((Long) -> Boolean)? = null,
) {
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
                getNumberOfItemsForCollection = getNumberOfNovelsForCollection,
                onTabItemClick = { scope.launch { pagerState.animateScrollToPage(it) } },
                sortLabel = sortLabel,
                sortDescending = sortDescending,
                onSortClick = onSortClick,
            )
        } else {
            val currentCollection = collections.getOrNull(pagerState.currentPage)
            if (currentCollection != null) {
                CollectionHeaderRow(
                    title = currentCollection.let { if (it.name.isBlank()) "Default" else it.name },
                    itemCount = getNumberOfNovelsForCollection(currentCollection),
                    sortLabel = sortLabel,
                    sortDescending = sortDescending,
                    onSortClick = onSortClick,
                    showTitle = showLibraryTitle,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        val notSelectionMode = selection.isEmpty() && !readingOrderMode
        val onClickNovel = { novel: LibraryNovel ->
            if (notSelectionMode) {
                onNovelClicked(novel.novel.id)
            } else {
                onToggleSelection(novel)
            }
        }

        PullRefresh(
            refreshing = isRefreshing,
            onRefresh = {
                val started = onRefresh(collections[currentPage()])
                if (!started) return@PullRefresh
                scope.launch {
                    isRefreshing = true
                    delay(1.seconds)
                    isRefreshing = false
                }
            },
            enabled = notSelectionMode,
        ) {
            NovelLibraryPager(
                state = pagerState,
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                hasActiveFilters = hasActiveFilters,
                selectedNovels = selection,
                searchQuery = searchQuery,
                onGlobalSearchClicked = onGlobalSearchClicked,
                getDisplayMode = getDisplayMode,
                getColumnsForOrientation = getColumnsForOrientation,
                getLibraryForPage = getLibraryForPage,
                onClickNovel = onClickNovel,
                onLongClickNovel = onToggleRangeSelection,
                onClickContinueReading = onContinueReadingClicked,
                getReadingOrderLayer = getReadingOrderLayer,
                getPreviousLayerNovelIds = getPreviousLayerNovelIds,
                isEntryLocked = isEntryLocked,
            )
        }

        LaunchedEffect(pagerState.currentPage) {
            onChangeCurrentPage(pagerState.currentPage)
        }
    }
}
