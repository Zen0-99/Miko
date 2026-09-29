package eu.kanade.tachiyomi.ui.metadata.canonical

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.InLibraryBadge
import eu.kanade.presentation.library.components.CommonEntryItemDefaults
import eu.kanade.presentation.library.components.EntryComfortableGridItem
import eu.kanade.presentation.library.components.EntryCompactGridItem
import eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
import eu.kanade.tachiyomi.data.mikodb.WorkIdResolver
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.util.collectAsState

/**
 * Canonical catalog grid — the manga/books counterpart of
 * [eu.kanade.tachiyomi.ui.metadata.cinemeta.CinemetaBrowseContent],
 * rendered within DiscoveryTab.
 *
 * Search-driven only: the map has no curated popularity feed, so an idle
 * surface shows a hint instead of a catalog. Tapping a result
 * materializes the canonical entry row and opens its detail screen.
 */
@Composable
fun CanonicalCatalogContent(
    screenModel: CanonicalCatalogScreenModel,
    medium: String,
    contentPadding: PaddingValues,
    libraryPreferences: LibraryPreferences,
) {
    val state by screenModel.state.collectAsState()
    val navigator = LocalNavigator.currentOrThrow
    val scope = rememberCoroutineScope()

    val gridState = rememberLazyGridState()
    val displayMode by libraryPreferences.displayMode().collectAsState()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isNovel = medium == WorkIdResolver.MEDIUM_BOOKS
    val columns by (
        if (isLandscape) {
            if (isNovel) libraryPreferences.novelLandscapeColumns() else libraryPreferences.mangaLandscapeColumns()
        } else {
            if (isNovel) libraryPreferences.novelPortraitColumns() else libraryPreferences.mangaPortraitColumns()
        }
        ).collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        PullRefresh(
            refreshing = state.loading && state.results.isNotEmpty(),
            enabled = state.results.isNotEmpty(),
            onRefresh = { state.searchQuery?.let(screenModel::search) },
            indicatorPadding = PaddingValues(top = contentPadding.calculateTopPadding()),
        ) {
            when {
                state.loading && state.results.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.error != null && state.results.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = state.error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = "Retry",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clickable { screenModel.retry() },
                        )
                    }
                }

                state.results.isEmpty() && !state.loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when {
                                state.searchQuery != null -> "No results for \"${state.searchQuery}\""
                                else -> "Search the ${if (isNovel) "novel" else "manga"} catalog"
                            },
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
                            start = contentPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                            end = contentPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                            top = 8.dp,
                            bottom = contentPadding.calculateBottomPadding() + 8.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(CommonEntryItemDefaults.GridHorizontalSpacer),
                        verticalArrangement = Arrangement.spacedBy(CommonEntryItemDefaults.GridVerticalSpacer),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.results.size) { index ->
                            val work = state.results[index]
                            val bound = work.mikoWorkId in state.boundEntryIds
                            val coverData = if (isNovel) {
                                NovelCover(
                                    novelId = work.mikoWorkId.hashCode().toLong(),
                                    sourceId = CanonicalCatalog.CANONICAL_SOURCE_ID,
                                    isNovelFavorite = bound,
                                    url = null,
                                    lastModified = 0L,
                                )
                            } else {
                                MangaCover(
                                    mangaId = work.mikoWorkId.hashCode().toLong(),
                                    sourceId = CanonicalCatalog.CANONICAL_SOURCE_ID,
                                    isMangaFavorite = bound,
                                    url = null,
                                    lastModified = 0L,
                                )
                            }
                            val onClick: () -> Unit = {
                                scope.launch {
                                    val entryId = screenModel.getOrCreateEntryId(work)
                                    if (isNovel) {
                                        navigator.push(NovelScreen(entryId, true))
                                    } else {
                                        navigator.push(MangaScreen(entryId, true))
                                    }
                                }
                            }
                            val onLongClick: () -> Unit = {}
                            val label = buildString {
                                append(work.title)
                                work.year?.let { append("\n$it") }
                            }

                            when (displayMode) {
                                LibraryDisplayMode.CompactGrid, LibraryDisplayMode.CoverOnlyGrid -> {
                                    EntryCompactGridItem(
                                        coverData = coverData,
                                        onClick = onClick,
                                        onLongClick = onLongClick,
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
                                        onLongClick = onLongClick,
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
                    }
                }
            }
        } // PullRefresh
    }
}
