package eu.kanade.tachiyomi.ui.metadata.cinemeta

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.library.components.CommonEntryItemDefaults
import eu.kanade.presentation.library.components.EntryComfortableGridItem
import eu.kanade.presentation.library.components.EntryCompactGridItem
import eu.kanade.tachiyomi.metadata.miko.dto.MikoMetaShort
import eu.kanade.tachiyomi.ui.entries.shows.ShowsScreen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.shows.model.AnimeCover
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.util.collectAsState

/**
 * Shows discovery catalog — rendered inline within DiscoveryTab.
 * Uses the user's library display mode preference for the grid layout.
 */
@Composable
fun CinemetaBrowseContent(
    screenModel: CinemetaBrowseScreenModel,
    contentPadding: PaddingValues,
    libraryPreferences: LibraryPreferences,
) {
    val state by screenModel.state.collectAsState()
    val navigator = LocalNavigator.currentOrThrow
    val scope = rememberCoroutineScope()

    val displayMode by libraryPreferences.displayMode().collectAsState()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val columns by (
        if (isLandscape) {
            libraryPreferences.showsLandscapeColumns()
        } else {
            libraryPreferences.showsPortraitColumns()
        }
        ).collectAsState()

    // Library-collection-style paging between the type pills: each page
    // keeps its own grid state, so scroll positions never transfer, and
    // the settled page commits the type to the screen model.
    val typeOptions = listOf("Movies" to "movie", "Series" to "series")
    val selectedIndex = typeOptions.indexOfFirst { it.second == state.type }
        .coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = selectedIndex) { typeOptions.size }
    LaunchedEffect(pagerState.settledPage) {
        val type = typeOptions[pagerState.settledPage].second
        if (type != state.type) screenModel.changeType(type)
    }
    LaunchedEffect(state.type) {
        if (typeOptions.getOrNull(pagerState.currentPage)?.second != state.type) {
            pagerState.animateScrollToPage(selectedIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        // Type toggle: Movies | Series — custom pill, no checkmark icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            typeOptions.forEachIndexed { index, option ->
                val (label, _) = option
                val isSelected = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .then(
                            if (isSelected) {
                                Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            } else {
                                Modifier
                            },
                        )
                        .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }

        val emptyText = if (state.searchQuery != null) {
            "No results for \"${state.searchQuery}\""
        } else {
            "No items found"
        }

        if (state.searchQuery != null) {
            // Search results are type-scoped already — a single grid, no
            // paging between pills while a query is live.
            CinemetaCatalogGrid(
                metas = state.metas,
                loading = state.loading,
                error = state.error,
                hasMore = state.hasMore,
                pagingEnabled = true,
                onLoadNext = { screenModel.loadNextPage() },
                onRefresh = { screenModel.refresh() },
                onRetry = { screenModel.retry() },
                onMetaClick = { meta ->
                    scope.launch {
                        navigator.push(ShowsScreen(screenModel.getOrCreateAnimeId(meta), true))
                    }
                },
                emptyText = emptyText,
                columns = columns,
                displayMode = displayMode,
                contentPadding = contentPadding,
            )
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val type = typeOptions[page].second
                val live = type == state.type
                CinemetaCatalogGrid(
                    metas = if (live) state.metas else screenModel.peekCachedMetas(type),
                    loading = live && state.loading,
                    error = if (live) state.error else null,
                    hasMore = live && state.hasMore,
                    pagingEnabled = live,
                    onLoadNext = { screenModel.loadNextPage() },
                    onRefresh = { screenModel.refresh() },
                    onRetry = { screenModel.retry() },
                    onMetaClick = { meta ->
                        scope.launch {
                            navigator.push(ShowsScreen(screenModel.getOrCreateAnimeId(meta), true))
                        }
                    },
                    emptyText = emptyText,
                    columns = columns,
                    displayMode = displayMode,
                    contentPadding = contentPadding,
                )
            }
        }
    }
}

/**
 * One catalog grid page — items, paging, pull-refresh and the loading/
 * error/empty chrome. [pagingEnabled] is false for pager pages that
 * preview a cached, non-live type: their scroll must not page the live
 * feed and pull-refresh stays disabled until they settle.
 */
@Composable
private fun CinemetaCatalogGrid(
    metas: List<MikoMetaShort>,
    loading: Boolean,
    error: String?,
    hasMore: Boolean,
    pagingEnabled: Boolean,
    onLoadNext: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onMetaClick: (MikoMetaShort) -> Unit,
    emptyText: String,
    columns: Int,
    displayMode: LibraryDisplayMode,
    contentPadding: PaddingValues,
) {
    val gridState = rememberLazyGridState()

    // Infinite scroll: load next page when near the end
    LaunchedEffect(gridState) {
        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = gridState.layoutInfo.totalItemsCount
            lastVisible >= totalItems - 6
        }
            .distinctUntilChanged()
            .collect { shouldLoad ->
                if (shouldLoad && pagingEnabled && hasMore && !loading && error == null) {
                    onLoadNext()
                }
            }
    }

    PullRefresh(
        refreshing = loading && metas.isNotEmpty(),
        enabled = pagingEnabled && metas.isNotEmpty(),
        onRefresh = onRefresh,
        indicatorPadding = PaddingValues(top = contentPadding.calculateTopPadding()),
    ) {
        when {
            loading && metas.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            error != null && metas.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = "Retry",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable { onRetry() },
                    )
                }
            }

            metas.isEmpty() && !loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (pagingEnabled) {
                        Text(
                            text = emptyText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            else -> {
                val gridColumns = if (columns > 0) columns else 3
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    state = gridState,
                    contentPadding = PaddingValues(
                        start = contentPadding.calculateLeftPadding(LayoutDirection.Ltr),
                        end = contentPadding.calculateRightPadding(LayoutDirection.Ltr),
                        top = 8.dp,
                        bottom = contentPadding.calculateBottomPadding() + 8.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(CommonEntryItemDefaults.GridHorizontalSpacer),
                    verticalArrangement = Arrangement.spacedBy(CommonEntryItemDefaults.GridVerticalSpacer),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        count = metas.size,
                        key = { metas[it].id },
                    ) { index ->
                        val meta = metas[index]
                        val coverData = AnimeCover(
                            animeId = meta.id.hashCode().toLong(),
                            sourceId = CinemetaBrowseScreenModel.CINEMETA_SOURCE_ID,
                            isAnimeFavorite = false,
                            url = meta.poster,
                            lastModified = 0L,
                        )
                        val onClick: () -> Unit = { onMetaClick(meta) }
                        val onLongClick: () -> Unit = {}

                        when (displayMode) {
                            LibraryDisplayMode.CompactGrid, LibraryDisplayMode.CoverOnlyGrid -> {
                                EntryCompactGridItem(
                                    coverData = coverData,
                                    onClick = onClick,
                                    onLongClick = onLongClick,
                                    title = if (displayMode is LibraryDisplayMode.CompactGrid) {
                                        meta.name
                                    } else {
                                        null
                                    },
                                )
                            }

                            else -> {
                                EntryComfortableGridItem(
                                    title = meta.name,
                                    onClick = onClick,
                                    onLongClick = onLongClick,
                                    coverData = coverData,
                                )
                            }
                        }
                    }
                    if (loading) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    } // PullRefresh
}
