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
import eu.kanade.presentation.library.components.CommonEntryItemDefaults
import eu.kanade.presentation.library.components.EntryComfortableGridItem
import eu.kanade.presentation.library.components.EntryCompactGridItem
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

    // The feed follows the pill — the medium rides along so a carrier
    // switch can never fire a stale-medium request.
    LaunchedEffect(carrier, selectedIndex) {
        screenModel.setCatalogFeed(carrier.medium, pills[selectedIndex].kindsCsv)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SubTypePillRow(
            pills = pills,
            selectedIndex = selectedIndex,
            topPadding = contentPadding.calculateTopPadding(),
            onSelect = { discoveryScreenModel.selectSubType(carrier, it) },
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

/** Per-carrier pill set. [kindsCsv] null = the OpenLibrary trending feed. */
private class SubPill(val labelRes: StringResource, val kindsCsv: String?)

private fun pillsFor(carrier: Carrier): List<SubPill> = when (carrier) {
    Carrier.BOOKS -> listOf(
        // Webnovels live in the Worker's MangaBaka mirror; published
        // books come from OpenLibrary's trending feed.
        SubPill(AYMR.strings.discovery_pill_novels, "novel"),
        SubPill(AYMR.strings.carrier_books, null),
    )
    else -> listOf(
        SubPill(AYMR.strings.carrier_manga, "manga,manhwa,manhua"),
        // MangaBaka's type vocab has no comic/webtoon — webtoon-flavored
        // rows land in `oel`/`other`, which is what this pill queries.
        SubPill(AYMR.strings.discovery_pill_webtoons, "webtoon,oel,other"),
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
    screenModel: CanonicalCatalogScreenModel,
    contentPadding: PaddingValues,
    libraryPreferences: LibraryPreferences,
) {
    val state by screenModel.state.collectAsState()
    val navigator = LocalNavigator.currentOrThrow
    val scope = rememberCoroutineScope()

    val gridState = rememberLazyGridState()
    val displayMode by libraryPreferences.displayMode().collectAsState()

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

    // Infinite scroll: load next page when near the end
    LaunchedEffect(gridState) {
        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = gridState.layoutInfo.totalItemsCount
            lastVisible >= totalItems - 6
        }
            .distinctUntilChanged()
            .collect { shouldLoad ->
                if (shouldLoad && state.browseHasMore && !state.browseLoading && state.browseError == null) {
                    screenModel.loadNextBrowsePage()
                }
            }
    }

    PullRefresh(
        refreshing = state.browseLoading && state.browseItems.isNotEmpty(),
        enabled = state.browseItems.isNotEmpty(),
        onRefresh = { screenModel.refreshBrowse() },
        indicatorPadding = PaddingValues(top = contentPadding.calculateTopPadding()),
    ) {
        when {
            state.browseLoading && state.browseItems.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.browseError != null && state.browseItems.isEmpty() -> {
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

            state.browseItems.isEmpty() && !state.browseLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(AYMR.strings.discovery_catalog_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                    items(state.browseItems.size) { index ->
                        val item = state.browseItems[index]
                        val work = item.work

                        // Mint-on-browse: once per work id per session;
                        // the model dedupes and throttles off-thread.
                        LaunchedEffect(work.mikoWorkId) {
                            screenModel.onCatalogItemRendered(item)
                        }

                        val coverData = if (isNovel) {
                            NovelCover(
                                novelId = work.mikoWorkId.hashCode().toLong(),
                                sourceId = CanonicalCatalog.CANONICAL_SOURCE_ID,
                                isNovelFavorite = false,
                                url = item.coverUrl,
                                lastModified = 0L,
                            )
                        } else {
                            MangaCover(
                                mangaId = work.mikoWorkId.hashCode().toLong(),
                                sourceId = CanonicalCatalog.CANONICAL_SOURCE_ID,
                                isMangaFavorite = false,
                                url = item.coverUrl,
                                lastModified = 0L,
                            )
                        }
                        val onClick: () -> Unit = {
                            scope.launch {
                                val entryId = screenModel.materializeEntryId(work, carrier.medium)
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
                                )
                            }

                            else -> {
                                EntryComfortableGridItem(
                                    title = label,
                                    onClick = onClick,
                                    onLongClick = {},
                                    coverData = coverData,
                                )
                            }
                        }
                    }
                    if (state.browseLoading) {
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
