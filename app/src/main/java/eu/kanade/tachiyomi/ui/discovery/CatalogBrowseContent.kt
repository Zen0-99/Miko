package eu.kanade.tachiyomi.ui.discovery

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.ui.model.Carrier
import eu.kanade.presentation.browse.DominoRevealItem
import eu.kanade.presentation.browse.InLibraryBadge
import eu.kanade.presentation.browse.rememberDominoReveal
import eu.kanade.presentation.library.components.CommonEntryItemDefaults
import eu.kanade.presentation.library.components.armSharedCover
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
 * Manga/books catalog surface for DiscoveryTab (Phase 15-07): a ranked
 * catalog grid — the Worker's `/v1/works/browse` for the manga/novels
 * sub-types, the seeded book chart for "Books". The sub-type and feed
 * sort live in the shared Type/Filter sheet ([DiscoveryTypeRow] opens
 * it); search submissions swap the grid to canonical map results
 * inline (same semantics as the shows carrier's discover swap).
 *
 * The selection persists per-carrier in
 * [DiscoveryScreenModel.State.subTypeSelections]; the feed sort in
 * [DiscoveryScreenModel.State.browseSorts].
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
    val browseSort = discoveryScreenModel.browseSortFor(carrier)

    // The feed follows the committed selection + sort — the medium rides
    // along so a carrier switch can never fire a stale-medium request.
    // During a live search a type change re-scopes the results instead
    // of swapping an invisible feed; the search-active key also refires
    // when results close so the remounted grid reloads the committed
    // sub-type's feed.
    LaunchedEffect(carrier, selectedIndex, browseSort, state.searchQuery != null) {
        val query = state.searchQuery
        if (query != null) {
            val kinds = pills[selectedIndex].searchKindsCsv
            if (state.searchKindsCsv != kinds) {
                screenModel.search(query, kinds)
            }
        } else {
            screenModel.setCatalogFeed(
                carrier.medium,
                pills[selectedIndex].kindsCsv,
                browseSort.catalogParam,
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        DiscoveryTypeRow(
            label = stringResource(pills[selectedIndex].labelRes),
            topPadding = contentPadding.calculateTopPadding(),
            onTypeClick = { discoveryScreenModel.openTypeFilterSheet(0) },
        )
        // The type row consumed the top inset — the grid/search branches
        // get start/end/bottom only.
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
                CatalogBrowseGrid(
                    carrier = carrier,
                    screenModel = screenModel,
                    contentPadding = innerPadding,
                    libraryPreferences = libraryPreferences,
                )
            }
        }
    }
}

/** Per-carrier sub-type set. [kindsCsv] null on BOOKS = the seeded book
 *  chart (`books|book`, live OL trending fallback until seeded);
 *  [searchKindsCsv] scopes a live search to the sub-type's kinds — it
 *  differs from [kindsCsv] only for the "Books" option, whose browse
 *  feed is chart-keyed while its search scope is the `book` kind. */
private class SubType(
    val labelRes: StringResource,
    val kindsCsv: String?,
    val searchKindsCsv: String?,
)

private fun pillsFor(carrier: Carrier): List<SubType> = when (carrier) {
    Carrier.BOOKS -> listOf(
        // Webnovels live in the Worker's MangaBaka mirror; published
        // books come from the MikoDB book chart (OpenLibrary trending
        // until the seed lands).
        SubType(AYMR.strings.discovery_pill_novels, "novel", "novel"),
        SubType(AYMR.strings.carrier_books, null, "book"),
        // 22-06 (B): kind='audiobook' works mint when ABS/LibriVox/IA
        // entries resolve — the feed fills as audio gets browsed.
        SubType(AYMR.strings.discovery_pill_audiobooks, "audiobook", "audiobook"),
    )
    else -> listOf(
        SubType(AYMR.strings.carrier_manga, "manga,comic", "manga,comic"),
        // All vertical-scroll content lives here — platform-linked
        // works carry the derived `webtoon` kind; the worker's
        // kind-filtered feed backs the option, no extension needed.
        SubType(AYMR.strings.discovery_pill_webtoons, WEBTOON_KINDS_CSV, WEBTOON_KINDS_CSV),
    )
}

/** Sub-type labels for the Type tab — the shows carrier's options
 *  (Movies|Series) live in `CinemetaBrowseScreenModel`, not pillsFor. */
internal fun discoveryTypeLabelsFor(carrier: Carrier): List<StringResource> = when (carrier) {
    Carrier.SHOWS -> listOf(AYMR.strings.catalog_type_movie, AYMR.strings.catalog_type_series)
    else -> pillsFor(carrier).map { it.labelRes }
}

/** Kind scope for the carrier's currently-selected sub-type — the
 *  top-bar search submit uses it so results match the visible
 *  sub-type. */
internal fun searchKindsCsvFor(carrier: Carrier, selectedIndex: Int): String? =
    pillsFor(carrier).getOrNull(selectedIndex)?.searchKindsCsv

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
    screenModel: CanonicalCatalogScreenModel,
    contentPadding: PaddingValues,
    libraryPreferences: LibraryPreferences,
) {
    val state by screenModel.state.collectAsState()
    val navigator = LocalNavigator.currentOrThrow
    val scope = rememberCoroutineScope()

    // Each sub-type's feed keeps its own scroll slot — the save key rides
    // the live feed (kinds + sort) so switching types doesn't leak
    // offsets, and switching back restores the earlier position.
    val gridState = rememberSaveable(
        state.browseFeed,
        saver = LazyGridState.Saver,
        key = "catalog-grid:${carrier.medium}:${state.browseFeed ?: "none"}",
    ) { LazyGridState() }
    val displayMode by libraryPreferences.displayMode().collectAsState()

    val items = state.browseItems
    val boundEntryIds = state.boundEntryIds
    val loading = state.browseLoading
    val error = state.browseError

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

    // Infinite scroll: load next page when near the end. The gating
    // reads happen inside the snapshotFlow so a cold-start `loading`
    // can't freeze paging, and a feed re-selection (sub-type or sort)
    // is observed live rather than captured at composition.
    // The top indicator answers manual pulls only — PullRefresh latches
    // its spinner on the pull gesture itself, so pagination and feed
    // re-selections still load silently.

    // Shared-element latch — the tapped card flies to the detail hero
    // under the library cell's `cover-image-{entryId}`, but unbound rows
    // only get an entry id once the tap resolves it (materialize). The
    // card re-keys and is laid out before the push (armSharedCover);
    // bound rows already know theirs via boundEntry.
    var navSharedEntry by rememberSaveable { mutableStateOf<Pair<String, Long>?>(null) }

    LaunchedEffect(gridState) {
        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = gridState.layoutInfo.totalItemsCount
            val s = state
            lastVisible >= totalItems - 6 &&
                s.browseFeed != null &&
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
        refreshing = loading && items.isNotEmpty(),
        enabled = items.isNotEmpty(),
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
                    if (state.browseFeed != null) {
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
                val domino = rememberDominoReveal<String>(
                    scope = "catalog:${carrier.medium}",
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

                        // Bound rows carry the real entry id — the cover
                        // keys (shared element + bitmap memory key) match
                        // the detail hero's exactly, like a library cell.
                        // Unbound rows key off the work hash until the
                        // tap resolves the id (navSharedEntry).
                        val coverEntryId = boundEntry?.entryId
                            ?: work.mikoWorkId.hashCode().toLong()
                        val coverData = if (isNovel) {
                            NovelCover(
                                novelId = coverEntryId,
                                sourceId = CanonicalCatalog.CANONICAL_SOURCE_ID,
                                isNovelFavorite = bound,
                                url = coverUrl,
                                lastModified = 0L,
                            )
                        } else {
                            MangaCover(
                                mangaId = coverEntryId,
                                sourceId = CanonicalCatalog.CANONICAL_SOURCE_ID,
                                isMangaFavorite = bound,
                                url = coverUrl,
                                lastModified = 0L,
                            )
                        }
                        val heroEntryId = boundEntry?.entryId ?: navSharedEntry
                            ?.takeIf { it.first == work.mikoWorkId }
                            ?.second
                        val onClick: () -> Unit = {
                            scope.launch {
                                val entryId = screenModel.entryIdFor(item, carrier.medium)
                                    ?: return@launch
                                armSharedCover(coverData, entryId) {
                                    navSharedEntry = work.mikoWorkId to entryId
                                }
                                navigator.push(
                                    if (isNovel) {
                                        NovelScreen(
                                            entryId,
                                            true,
                                            previewCover = coverData,
                                            previewTitle = work.title,
                                        )
                                    } else {
                                        MangaScreen(
                                            entryId,
                                            true,
                                            previewCover = coverData,
                                            previewTitle = work.title,
                                        )
                                    },
                                )
                            }
                        }
                        val label = work.title

                        DominoRevealItem(visible = domino.isVisible(work.mikoWorkId)) {
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
                                        sharedCoverEntryId = heroEntryId,
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
