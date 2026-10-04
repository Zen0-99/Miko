package eu.kanade.tachiyomi.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import eu.kanade.domain.ui.model.Carrier
import eu.kanade.presentation.library.components.CommonEntryItemDefaults
import eu.kanade.presentation.library.components.EntryComfortableGridItem
import eu.kanade.presentation.library.components.EntryCompactGridItem
import eu.kanade.presentation.library.filter.FilterTagChip
import eu.kanade.tachiyomi.metadata.miko.dto.MikoMetaShort
import eu.kanade.tachiyomi.metadata.tmdb.TmdbDiscover
import eu.kanade.tachiyomi.metadata.tmdb.TmdbGenres
import eu.kanade.tachiyomi.metadata.tmdb.TmdbGenres.MediaType
import eu.kanade.tachiyomi.metadata.tmdb.TmdbSubGenreKeywords
import eu.kanade.tachiyomi.ui.entries.shows.ShowsScreen
import eu.kanade.tachiyomi.ui.metadata.cinemeta.CinemetaBrowseContent
import eu.kanade.tachiyomi.ui.metadata.cinemeta.CinemetaBrowseScreenModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.entries.shows.model.AnimeCover
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

/**
 * Shows-mode content for DiscoveryTab (Phase 15-06): a facet filter bar
 * above the catalog grid; when any facet is active the grid swaps the
 * Cinemeta catalog for TMDB `discover` results (enrichment only). Media
 * type stays owned by [CinemetaBrowseScreenModel] — the Movies/Series
 * pills are rendered identically in both modes.
 */
@Composable
fun ShowsDiscoveryContent(
    discoveryScreenModel: DiscoveryScreenModel,
    cinemetaScreenModel: CinemetaBrowseScreenModel,
    contentPadding: PaddingValues,
    libraryPreferences: LibraryPreferences,
) {
    val state by discoveryScreenModel.state.collectAsState()
    val cinemetaState by cinemetaScreenModel.state.collectAsState()
    val navigator = LocalNavigator.currentOrThrow

    val filter = state.filters[Carrier.SHOWS] ?: DiscoveryFilter()
    val mediaType = if (cinemetaState.type == "movie") MediaType.MOVIE else MediaType.TV

    // Single funnel for filter/type changes — the model debounces the
    // discover reload so chip toggles don't fire a request per tap.
    LaunchedEffect(filter, mediaType) {
        discoveryScreenModel.onDiscoverInputsChanged(mediaType)
    }

    // When the active-facet chip row is hidden it can't consume the top
    // inset — hand it back to the catalog/discover content.
    val innerPadding = PaddingValues(
        start = contentPadding.calculateLeftPadding(LayoutDirection.Ltr),
        end = contentPadding.calculateRightPadding(LayoutDirection.Ltr),
        top = if (filter.isActive) 0.dp else contentPadding.calculateTopPadding(),
        bottom = contentPadding.calculateBottomPadding(),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        if (filter.isActive) {
            DiscoveryFilterBar(
                filter = filter,
                mediaType = mediaType,
                topPadding = contentPadding.calculateTopPadding(),
                onClear = { discoveryScreenModel.setFilter(Carrier.SHOWS, DiscoveryFilter()) },
                onRemoveGenre = {
                    discoveryScreenModel.setFilter(Carrier.SHOWS, filter.withGenreState(it, TriState.DISABLED))
                },
                onRemoveKeyword = {
                    discoveryScreenModel.setFilter(Carrier.SHOWS, filter.withKeywordState(it, TriState.DISABLED))
                },
                onRemoveYear = {
                    discoveryScreenModel.setFilter(Carrier.SHOWS, filter.copy(yearFrom = null, yearTo = null))
                },
                onRemoveRating = {
                    discoveryScreenModel.setFilter(
                        Carrier.SHOWS,
                        filter.copy(voteAverageGte = null, voteCountGte = null),
                    )
                },
                onRemoveSort = {
                    discoveryScreenModel.setFilter(Carrier.SHOWS, filter.copy(sort = DiscoverySort.POPULARITY))
                },
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            if (filter.isActive) {
                TmdbDiscoverContent(
                    discoveryScreenModel = discoveryScreenModel,
                    cinemetaScreenModel = cinemetaScreenModel,
                    mediaType = mediaType,
                    contentPadding = innerPadding,
                    libraryPreferences = libraryPreferences,
                )
            } else {
                CinemetaBrowseContent(
                    screenModel = cinemetaScreenModel,
                    contentPadding = innerPadding,
                    libraryPreferences = libraryPreferences,
                )
            }
        }
    }

    if (state.showFilterSheet) {
        DiscoveryFilterSheet(
            filter = filter,
            mediaType = mediaType,
            onFilterChange = { discoveryScreenModel.setFilter(Carrier.SHOWS, it) },
            onDismiss = { discoveryScreenModel.setFilterSheetVisible(false) },
        )
    }
}

/**
 * Active-facet row over the grid — the collapsed `PersistentFilterSheet`
 * idiom minus the icon (that moved to the shared top bar): the active
 * facets as removable chips plus an inline clear affordance. Only
 * composed while [DiscoveryFilter.isActive].
 */
@Composable
private fun DiscoveryFilterBar(
    filter: DiscoveryFilter,
    mediaType: MediaType,
    topPadding: androidx.compose.ui.unit.Dp,
    onClear: () -> Unit,
    onRemoveGenre: (Int) -> Unit,
    onRemoveKeyword: (Int) -> Unit,
    onRemoveYear: () -> Unit,
    onRemoveRating: () -> Unit,
    onRemoveSort: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filter.genreStates.forEach { (id, triState) ->
            if (triState == TriState.DISABLED) return@forEach
            FilterTagChip(
                label = TmdbGenres.nameForId(id) ?: id.toString(),
                state = triState,
                enabled = true,
                onToggle = { onRemoveGenre(id) },
            )
        }
        filter.keywordStates.forEach { (id, triState) ->
            if (triState == TriState.DISABLED) return@forEach
            FilterTagChip(
                label = TmdbSubGenreKeywords.nameForKeyword(id) ?: id.toString(),
                state = triState,
                enabled = true,
                onToggle = { onRemoveKeyword(id) },
            )
        }
        if (filter.yearFrom != null || filter.yearTo != null) {
            FilterTagChip(
                label = "${filter.yearFrom ?: "…"}–${filter.yearTo ?: "…"}",
                state = TriState.ENABLED_IS,
                enabled = true,
                onToggle = onRemoveYear,
            )
        }
        if (filter.voteAverageGte != null) {
            FilterTagChip(
                label = "★ ${filter.voteAverageGte}+",
                state = TriState.ENABLED_IS,
                enabled = true,
                onToggle = onRemoveRating,
            )
        }
        if (filter.sort != DiscoverySort.POPULARITY) {
            FilterTagChip(
                label = stringResource(filter.sort.labelRes),
                state = TriState.ENABLED_IS,
                enabled = true,
                onToggle = onRemoveSort,
            )
        }

        if (filter.isActive) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(MR.strings.clear_filters),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * TMDB-faceted grid — mirrors CinemetaBrowseContent's layout/display
 * modes. Tapping routes through TMDB external_ids → IMDB id → the
 * existing `cinemeta:{type}:{ttid}` entry path; entries without an IMDB
 * link are non-navigable (minimal viable behavior per plan).
 */
@Composable
private fun TmdbDiscoverContent(
    discoveryScreenModel: DiscoveryScreenModel,
    cinemetaScreenModel: CinemetaBrowseScreenModel,
    mediaType: MediaType,
    contentPadding: PaddingValues,
    libraryPreferences: LibraryPreferences,
) {
    val state by discoveryScreenModel.state.collectAsState()
    val cinemetaState by cinemetaScreenModel.state.collectAsState()
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

    // Same Movies | Series pill as CinemetaBrowseContent — bound to the
    // same screen-model field so both surfaces stay in sync, but paged:
    // each page keeps its own grid state (scroll positions never carry
    // over) and the settled page commits via changeType.
    val typeOptions = listOf("Movies" to MediaType.MOVIE, "Series" to MediaType.TV)
    val selectedIndex = typeOptions.indexOfFirst { it.second == mediaType }
        .coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = selectedIndex) { typeOptions.size }
    LaunchedEffect(pagerState.settledPage) {
        val settled = typeOptions[pagerState.settledPage].second
        val type = if (settled == MediaType.MOVIE) "movie" else "series"
        if (type != cinemetaState.type) cinemetaScreenModel.changeType(type)
    }
    LaunchedEffect(mediaType) {
        if (typeOptions.getOrNull(pagerState.currentPage)?.second != mediaType) {
            pagerState.animateScrollToPage(selectedIndex)
        }
    }
    val filter = state.filters[Carrier.SHOWS] ?: DiscoveryFilter()

    Column(modifier = Modifier.fillMaxSize()) {
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
                val label = if (option.second == MediaType.MOVIE) "Movies" else "Series"
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

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            val pageType = typeOptions[page].second
            val live = pageType == mediaType
            TmdbDiscoverGrid(
                items = if (live) {
                    state.discoverItems
                } else {
                    discoveryScreenModel.peekDiscoverItems(pageType, filter)
                },
                loading = live && state.discoverLoading,
                error = live && state.discoverError,
                keyMissing = live && state.discoverKeyMissing,
                hasMore = live && state.discoverHasMore,
                pagingEnabled = live,
                mediaType = pageType,
                discoveryScreenModel = discoveryScreenModel,
                onItemClick = { item ->
                    scope.launch {
                        // TMDB → IMDB → existing cinemeta entry path; no
                        // IMDB link = dead end.
                        val imdb = discoveryScreenModel.resolveImdbId(pageType, item.id)
                            ?: return@launch
                        val meta = MikoMetaShort(
                            id = imdb,
                            name = item.title,
                            type = TmdbDiscover.cinemetaType(pageType),
                            poster = item.posterUrl,
                            releaseInfo = item.year?.toString(),
                            imdbRating = item.voteAverage?.toString(),
                        )
                        val animeId = cinemetaScreenModel.getOrCreateAnimeId(meta)
                        navigator.push(ShowsScreen(animeId, true))
                    }
                },
                onRefresh = { discoveryScreenModel.onDiscoverInputsChanged(pageType) },
                columns = columns,
                displayMode = displayMode,
                contentPadding = contentPadding,
            )
        }
    }
}

/**
 * One discover-grid page — mirrors the catalog grid's chrome. Preview
 * pages ([pagingEnabled] false) show their own cached items; paging and
 * pull-refresh only run on the live page so one type's scroll can't
 * page the other's feed.
 */
@Composable
private fun TmdbDiscoverGrid(
    items: List<TmdbDiscover.Item>,
    loading: Boolean,
    error: Boolean,
    keyMissing: Boolean,
    hasMore: Boolean,
    pagingEnabled: Boolean,
    mediaType: MediaType,
    discoveryScreenModel: DiscoveryScreenModel,
    onItemClick: (TmdbDiscover.Item) -> Unit,
    onRefresh: () -> Unit,
    columns: Int,
    displayMode: LibraryDisplayMode,
    contentPadding: PaddingValues,
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState) {
        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = gridState.layoutInfo.totalItemsCount
            lastVisible >= totalItems - 6
        }
            .distinctUntilChanged()
            .collect { shouldLoad ->
                if (shouldLoad && pagingEnabled && hasMore && !loading && !error) {
                    discoveryScreenModel.loadNextDiscoverPage(mediaType)
                }
            }
    }

    PullRefresh(
        refreshing = loading && items.isNotEmpty(),
        enabled = pagingEnabled && items.isNotEmpty(),
        onRefresh = onRefresh,
        indicatorPadding = contentPadding,
    ) {
        when {
            keyMissing -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(AYMR.strings.discovery_tmdb_missing_key),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }

            loading && items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            error && items.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(AYMR.strings.discovery_tmdb_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = stringResource(MR.strings.action_retry),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable { onRefresh() },
                    )
                }
            }

            items.isEmpty() && !loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (pagingEnabled) {
                        Text(
                            text = stringResource(AYMR.strings.discovery_tmdb_empty),
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
                        count = items.size,
                        key = { items[it].id },
                    ) { index ->
                        val item = items[index]

                            // Mint-on-browse: TMDB discover rows aren't
                            // canonical works — resolve once per item
                            // (imdb → mk:imdb: mint) then markSeen. The
                            // model dedupes and throttles off-thread.
                            LaunchedEffect(item.id, mediaType) {
                                discoveryScreenModel.onDiscoverItemRendered(item)
                            }

                            val coverData = AnimeCover(
                                animeId = item.id,
                                sourceId = CinemetaBrowseScreenModel.CINEMETA_SOURCE_ID,
                                isAnimeFavorite = false,
                                url = item.posterUrl,
                                lastModified = 0L,
                            )
                            val onClick: () -> Unit = { onItemClick(item) }

                            when (displayMode) {
                                LibraryDisplayMode.CompactGrid, LibraryDisplayMode.CoverOnlyGrid -> {
                                    EntryCompactGridItem(
                                        coverData = coverData,
                                        onClick = onClick,
                                        onLongClick = {},
                                        title = if (displayMode is LibraryDisplayMode.CompactGrid) {
                                            item.title
                                        } else {
                                            null
                                        },
                                    )
                                }

                                else -> {
                                    EntryComfortableGridItem(
                                        title = item.title,
                                        onClick = onClick,
                                        onLongClick = {},
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
        }
    }
