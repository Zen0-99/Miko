package eu.kanade.tachiyomi.ui.metadata.cinemeta

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.DominoRevealItem
import eu.kanade.presentation.browse.rememberDominoReveal
import eu.kanade.presentation.library.components.CommonEntryItemDefaults
import eu.kanade.presentation.library.components.EntryComfortableGridItem
import eu.kanade.presentation.library.components.EntryCompactGridItem
import eu.kanade.presentation.library.components.armSharedCover
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
 * The Movies/Series switch lives in the shared Type/Filter sheet;
 * `changeType` hydrates the new type's grid (and re-runs live searches).
 */
@Composable
fun CinemetaBrowseContent(
    screenModel: CinemetaBrowseScreenModel,
    contentPadding: PaddingValues,
    libraryPreferences: LibraryPreferences,
) {
    val state by screenModel.state.collectAsState()

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        val emptyText = if (state.searchQuery != null) {
            "No results for \"${state.searchQuery}\""
        } else {
            "No items found"
        }

        CinemetaCatalogGrid(
            metas = state.metas,
            loading = state.loading,
            error = state.error,
            hasMore = state.hasMore,
            pagingEnabled = true,
            onLoadNext = { screenModel.loadNextPage() },
            onRefresh = { screenModel.refresh() },
            onRetry = { screenModel.retry() },
            resolveEntryId = { screenModel.getOrCreateAnimeId(it) },
            emptyText = emptyText,
            columns = columns,
            displayMode = displayMode,
            contentPadding = contentPadding,
            dominoScope = if (state.searchQuery != null) "search:${state.type}" else state.type,
        )
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
    /** Tap-time id resolution — the grid re-keys the tapped cell under
     *  `cover-image-{id}` and pushes with the preview cover/title, so
     *  the card morphs into the detail hero like a library cell. */
    resolveEntryId: suspend (MikoMetaShort) -> Long,
    emptyText: String,
    columns: Int,
    displayMode: LibraryDisplayMode,
    contentPadding: PaddingValues,
    /** Domino-reveal bucket — "movie"/"series" so each feed's cascade
     *  plays once per cold boot, not per tab hop. */
    dominoScope: String,
) {
    // Per-feed scroll slot — an explicit per-type key stops scroll
    // offsets leaking between the Movies/Series sub-types, and passing
    // the scope as an input restores each type's own position on a
    // switch (same fix as CatalogBrowseGrid).
    val gridState = rememberSaveable(
        dominoScope,
        saver = LazyGridState.Saver,
        key = "cinemeta-grid:$dominoScope",
    ) { LazyGridState() }
    val navigator = LocalNavigator.currentOrThrow
    val scope = rememberCoroutineScope()

    // The top indicator answers manual pulls only — PullRefresh latches
    // its spinner on the pull gesture itself, so pagination and feed
    // reloads still load silently.

    // Shared-element latch — the tapped card flies to the detail hero
    // under the library cell's `cover-image-{animeId}`; the id only
    // exists after the tap resolves it, so the card re-keys before the
    // push (armSharedCover).
    var navSharedEntry by rememberSaveable { mutableStateOf<Pair<String, Long>?>(null) }

    // Infinite scroll: load next page when near the end. The gating reads
    // must live inside snapshotFlow via rememberUpdatedState — a plain
    // captured param freezes at first composition, and a cold-start
    // `loading=true` would permanently disable paging (one 50-item page).
    val currentPagingEnabled by rememberUpdatedState(pagingEnabled)
    val currentHasMore by rememberUpdatedState(hasMore)
    val currentLoading by rememberUpdatedState(loading)
    val currentError by rememberUpdatedState(error)
    val loadNext by rememberUpdatedState(onLoadNext)

    LaunchedEffect(gridState) {
        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = gridState.layoutInfo.totalItemsCount
            lastVisible >= totalItems - 6 &&
                currentPagingEnabled &&
                currentHasMore &&
                !currentLoading &&
                currentError == null
        }
            .distinctUntilChanged()
            .collect { shouldLoad ->
                if (shouldLoad) loadNext()
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
                val domino = rememberDominoReveal<String>(
                    scope = "cinemeta:$dominoScope",
                )
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
                        val heroEntryId = navSharedEntry
                            ?.takeIf { it.first == meta.id }
                            ?.second
                        val onClick: () -> Unit = {
                            scope.launch {
                                val animeId = resolveEntryId(meta)
                                armSharedCover(coverData, animeId) { navSharedEntry = meta.id to animeId }
                                navigator.push(
                                    ShowsScreen(
                                        animeId,
                                        true,
                                        previewCover = coverData,
                                        previewTitle = meta.name,
                                        previewHasGenres = !meta.genres.isNullOrEmpty(),
                                    ),
                                )
                            }
                        }
                        val onLongClick: () -> Unit = {}

                        DominoRevealItem(visible = domino.isVisible(meta.id)) {
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
                                        sharedCoverEntryId = heroEntryId,
                                    )
                                }

                                else -> {
                                    EntryComfortableGridItem(
                                        title = meta.name,
                                        onClick = onClick,
                                        onLongClick = onLongClick,
                                        coverData = coverData,
                                        sharedCoverEntryId = heroEntryId,
                                    )
                                }
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
