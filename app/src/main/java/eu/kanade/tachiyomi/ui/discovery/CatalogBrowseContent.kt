package eu.kanade.tachiyomi.ui.discovery

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.ui.model.Carrier
import eu.kanade.presentation.browse.InLibraryBadge
import eu.kanade.presentation.library.components.CommonEntryItemDefaults
import eu.kanade.presentation.library.components.EntryComfortableGridItem
import eu.kanade.presentation.library.components.EntryCompactGridItem
import eu.kanade.tachiyomi.data.catalogrows.WEBTOON_KINDS_CSV
import eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.metadata.canonical.CanonicalCatalogContent
import eu.kanade.tachiyomi.ui.metadata.canonical.CanonicalCatalogScreenModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

/**
 * Manga/books catalog surface for DiscoveryTab (Phase 15-07): sub-type
 * pills over a ranked catalog grid — the Worker's `/v1/works/browse`
 * for the manga/novels pills, OpenLibrary trending for the books
 * "Books" pill. Search submissions swap the grid to the canonical map
 * results inline (same semantics as the shows carrier's discover swap).
 *
 * Pill chrome mirrors the shows Movies | Series toggle; the selection
 * persists per-carrier in [DiscoveryScreenModel.State.subTypeSelections].
 */
@Composable
fun CatalogBrowseContent(
    carrier: Carrier,
    discoveryScreenModel: DiscoveryScreenModel,
    screenModel: CanonicalCatalogScreenModel,
    contentPadding: PaddingValues,
    libraryPreferences: LibraryPreferences,
) {
    val discoveryState by discoveryScreenModel.state.collectAsState()
    val state by screenModel.state.collectAsState()

    val pills = pillsFor(carrier)
    val selectedIndex = (discoveryState.subTypeSelections[carrier] ?: 0)
        .coerceIn(0, pills.lastIndex)
    val scope = rememberCoroutineScope()

    // Library-collection-style paging: pill taps animate the pager and
    // the settled page commits the selection — each page keeps its own
    // grid state, so scroll positions never transfer between pills.
    val pagerState = rememberPagerState(initialPage = selectedIndex) { pills.size }
    LaunchedEffect(pagerState.settledPage) {
        val settled = pagerState.settledPage
        if (settled != selectedIndex) discoveryScreenModel.selectSubType(carrier, settled)
    }
    // Programmatic selection (restored state, carrier switch) → pager.
    LaunchedEffect(selectedIndex) {
        if (pagerState.currentPage != selectedIndex) {
            pagerState.animateScrollToPage(selectedIndex)
        }
    }

    // The feed follows the committed selection — the medium rides along
    // so a carrier switch can never fire a stale-medium request.
    LaunchedEffect(carrier, selectedIndex) {
        screenModel.setCatalogFeed(carrier.medium, pills[selectedIndex].kindsCsv)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SubTypePillRow(
            pills = pills,
            selectedIndex = pagerState.currentPage.coerceIn(0, pills.lastIndex),
            topPadding = contentPadding.calculateTopPadding(),
            onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
        )
        // Pills consumed the top inset — the grid/search branches get
        // start/end/bottom only.
        val innerPadding = PaddingValues(
            start = contentPadding.calculateLeftPadding(LayoutDirection.Ltr),
            end = contentPadding.calculateRightPadding(LayoutDirection.Ltr),
            top = 0.dp,
            bottom = contentPadding.calculateBottomPadding(),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (state.searchQuery != null) {
                CanonicalCatalogContent(
                    screenModel = screenModel,
                    medium = carrier.medium,
                    contentPadding = innerPadding,
                    libraryPreferences = libraryPreferences,
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    CatalogBrowseGrid(
                        carrier = carrier,
                        feedKindsCsv = pills[page].kindsCsv,
                        screenModel = screenModel,
                        contentPadding = innerPadding,
                        libraryPreferences = libraryPreferences,
                    )
                }
            }
        }
    }
}

/** Per-carrier pill set. [kindsCsv] null on BOOKS = the seeded book
 *  chart (`books|book`, live OL trending fallback until seeded). */
private class SubPill(val labelRes: StringResource, val kindsCsv: String?)

private fun pillsFor(carrier: Carrier): List<SubPill> = when (carrier) {
    Carrier.BOOKS -> listOf(
        // Webnovels live in the Worker's MangaBaka mirror; published
        // books come from the MikoDB book chart (OpenLibrary trending
        // until the seed lands).
        SubPill(AYMR.strings.discovery_pill_novels, "novel"),
        SubPill(AYMR.strings.carrier_books, null),
    )
    else -> listOf(
        SubPill(AYMR.strings.carrier_manga, "manga,comic"),
        // All vertical-scroll content lives here — platform-linked
        // works carry the derived `webtoon` kind; the worker's
        // kind-filtered feed backs the pill, no extension needed.
        SubPill(AYMR.strings.discovery_pill_webtoons, WEBTOON_KINDS_CSV),
    )
}

/**
 * Same track/tint as the shows Movies | Series toggle in
 * CinemetaBrowseContent/TmdbDiscoverContent — the carrier pill row.
 */
@Composable
private fun SubTypePillRow(
    pills: List<SubPill>,
    selectedIndex: Int,
    topPadding: Dp,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        pills.forEachIndexed { index, pill ->
            val isSelected = index == selectedIndex
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
                    .clickable { onSelect(index) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(pill.labelRes),
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
}

/**
 * Ranked catalog grid — mirrors the TMDB discover grid's chrome
 * (PullRefresh + loading/error/empty states + scroll-to-end paging).
 * Covers come straight off each row's `cover_url`/OL cover id; a tap
 * materializes the canonical entry and opens its detail screen.
 *
 * Each rendered item reports itself to [CanonicalCatalogScreenModel.
 * onCatalogItemRendered] once — browse rows get `markSeen` telemetry,
 * OpenLibrary rows resolve → mint → markSeen (fills the mint DB).
 */
@Composable
private fun CatalogBrowseGrid(
    carrier: Carrier,
    feedKindsCsv: String?,
    screenModel: CanonicalCatalogScreenModel,
    contentPadding: PaddingValues,
    libraryPreferences: LibraryPreferences,
) {
    val state by screenModel.state.collectAsState()
    val navigator = LocalNavigator.currentOrThrow
    val scope = rememberCoroutineScope()

    val gridState = rememberLazyGridState()
    val displayMode by libraryPreferences.displayMode().collectAsState()

    // A page is live only while the model's feed belongs to its pill —
    // matching on (medium, kindsCsv) rather than the raw feed key so the
    // webtoon pill's extension feed (`manga|webtoon:{pkg}`) still counts
    // as live. Non-live pages preview their own pill's cached items AND
    // its last-known library bindings, so the in-library badge rides the
    // preview frame instead of popping in after the swap.
    val liveFeed = state.browseFeed
    val live = liveFeed != null &&
        liveFeed.startsWith("${carrier.medium}|") &&
        state.browseKindsCsv == feedKindsCsv
    val items = if (live) {
        state.browseItems
    } else {
        screenModel.peekCachedFeed(carrier.medium, feedKindsCsv)
    }
    val boundEntryIds = if (live) {
        state.boundEntryIds
    } else {
        screenModel.peekCachedBound(carrier.medium, feedKindsCsv)
    }
    val loading = live && state.browseLoading
    val error = if (live) state.browseError else null

    val isNovel = carrier == Carrier.BOOKS
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val columns by (
        if (isLandscape) {
            if (isNovel) libraryPreferences.novelLandscapeColumns() else libraryPreferences.mangaLandscapeColumns()
        } else {
            if (isNovel) libraryPreferences.novelPortraitColumns() else libraryPreferences.mangaPortraitColumns()
        }
        ).collectAsState()

    // Infinite scroll: load next page when near the end — live page only,
    // a preview page's scroll must not page the other pill's feed. All
    // gating reads happen inside the snapshotFlow: `live`/`state` are
    // composition-time values and a LaunchedEffect captures the first
    // composition's `live` forever, freezing the pager when the grid
    // composes before its feed is selected.
    LaunchedEffect(gridState) {
        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = gridState.layoutInfo.totalItemsCount
            val s = state
            val isLive = s.browseFeed != null &&
                s.browseFeed.startsWith("${carrier.medium}|") &&
                s.browseKindsCsv == feedKindsCsv
            lastVisible >= totalItems - 6 &&
                isLive &&
                s.browseHasMore &&
                !s.browseLoading &&
                s.browseError == null
        }
            .distinctUntilChanged()
            .collect { shouldLoad ->
                if (shouldLoad) screenModel.loadNextBrowsePage()
            }
    }

    PullRefresh(
        refreshing = live && state.browseLoading && items.isNotEmpty(),
        enabled = live && items.isNotEmpty(),
        onRefresh = { screenModel.refreshBrowse() },
        indicatorPadding = PaddingValues(top = contentPadding.calculateTopPadding()),
    ) {
        when {
            loading && items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            error != null && items.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(AYMR.strings.discovery_catalog_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = stringResource(MR.strings.action_retry),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable { screenModel.retryBrowse() },
                    )
                }
            }

            items.isEmpty() && !loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (live) {
                        Text(
                            text = stringResource(AYMR.strings.discovery_catalog_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        CircularProgressIndicator()
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
                        // Stable work keys — the preview → live handoff
                        // swaps the list instance, not the rows.
                        key = { items[it].work.mikoWorkId },
                    ) { index ->
                        val item = items[index]
                        val work = item.work
                        val boundEntry = boundEntryIds[work.mikoWorkId]
                        val bound = boundEntry != null
                        // A bound card shows the library row's cover —
                        // discovery and library agree on the same entry.
                        val coverUrl = boundEntry?.coverUrl ?: item.coverUrl

                        // Mint-on-browse: once per work id per session;
                        // the model dedupes and throttles off-thread.
                        LaunchedEffect(work.mikoWorkId) {
                            screenModel.onCatalogItemRendered(item)
                        }

                        val coverData = if (isNovel) {
                            NovelCover(
                                novelId = work.mikoWorkId.hashCode().toLong(),
                                sourceId = CanonicalCatalog.CANONICAL_SOURCE_ID,
                                isNovelFavorite = bound,
                                url = coverUrl,
                                lastModified = 0L,
                            )
                        } else {
                            MangaCover(
                                mangaId = work.mikoWorkId.hashCode().toLong(),
                                sourceId = CanonicalCatalog.CANONICAL_SOURCE_ID,
                                isMangaFavorite = bound,
                                url = coverUrl,
                                lastModified = 0L,
                            )
                        }
                        val onClick: () -> Unit = {
                            scope.launch {
                                val entryId = screenModel.entryIdFor(item, carrier.medium)
                                    ?: return@launch
                                if (isNovel) {
                                    navigator.push(NovelScreen(entryId, true))
                                } else {
                                    navigator.push(MangaScreen(entryId, true))
                                }
                            }
                        }
                        val label = work.title

                        when (displayMode) {
                            LibraryDisplayMode.CompactGrid, LibraryDisplayMode.CoverOnlyGrid -> {
                                EntryCompactGridItem(
                                    coverData = coverData,
                                    onClick = onClick,
                                    onLongClick = {},
                                    title = if (displayMode is LibraryDisplayMode.CompactGrid) {
                                        label
                                    } else {
                                        null
                                    },
                                    coverAlpha = if (bound) {
                                        CommonEntryItemDefaults.BrowseFavoriteCoverAlpha
                                    } else {
                                        1f
                                    },
                                    coverBadgeStart = { InLibraryBadge(enabled = bound) },
                                )
                            }

                            else -> {
                                EntryComfortableGridItem(
                                    title = label,
                                    onClick = onClick,
                                    onLongClick = {},
                                    coverData = coverData,
                                    coverAlpha = if (bound) {
                                        CommonEntryItemDefaults.BrowseFavoriteCoverAlpha
                                    } else {
                                        1f
                                    },
                                    coverBadgeStart = { InLibraryBadge(enabled = bound) },
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
