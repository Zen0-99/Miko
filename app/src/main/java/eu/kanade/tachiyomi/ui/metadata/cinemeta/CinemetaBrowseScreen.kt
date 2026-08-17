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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.library.components.CommonEntryItemDefaults
import eu.kanade.presentation.library.components.EntryCompactGridItem
import eu.kanade.presentation.library.components.EntryComfortableGridItem
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.util.collectAsState

/**
 * Discover browse content — rendered inline within BrowseTab.
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

    val gridState = rememberLazyGridState()
    val displayMode by libraryPreferences.displayMode().collectAsState()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val columns by (
        if (isLandscape) {
            libraryPreferences.animeLandscapeColumns()
        } else {
            libraryPreferences.animePortraitColumns()
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
                if (shouldLoad && state.hasMore && !state.loading && state.error == null) {
                    screenModel.loadNextPage()
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        // Type toggle: Movies | Series — custom pill, no checkmark icon
        val typeOptions = listOf("Movies" to "movie", "Series" to "series")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            typeOptions.forEach { (label, value) ->
                val isSelected = state.type == value
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
                        .clickable { screenModel.changeType(value) }
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

        PullRefresh(
            refreshing = state.loading && state.metas.isNotEmpty(),
            enabled = state.metas.isNotEmpty(),
            onRefresh = { screenModel.refresh() },
            indicatorPadding = PaddingValues(top = contentPadding.calculateTopPadding()),
        ) {
        when {
            state.loading && state.metas.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.error != null && state.metas.isEmpty() -> {
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

            state.metas.isEmpty() && !state.loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.searchQuery != null) {
                            "No results for \"${state.searchQuery}\""
                        } else {
                            "No items found"
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
                    items(state.metas.size) { index ->
                        val meta = state.metas[index]
                        val coverData = AnimeCover(
                            animeId = meta.id.hashCode().toLong(),
                            sourceId = CinemetaBrowseScreenModel.CINEMETA_SOURCE_ID,
                            isAnimeFavorite = false,
                            url = meta.poster,
                            lastModified = 0L,
                        )
                        val onClick: () -> Unit = {
                            scope.launch {
                                val animeId = screenModel.getOrCreateAnimeId(meta)
                                navigator.push(AnimeScreen(animeId, true))
                            }
                        }
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

                            LibraryDisplayMode.ComfortableGrid -> {
                                EntryComfortableGridItem(
                                    title = meta.name,
                                    onClick = onClick,
                                    onLongClick = onLongClick,
                                    coverData = coverData,
                                )
                            }

                            LibraryDisplayMode.List -> {
                                // Fall back to comfortable grid for list mode in discover
                                EntryComfortableGridItem(
                                    title = meta.name,
                                    onClick = onClick,
                                    onLongClick = onLongClick,
                                    coverData = coverData,
                                )
                            }
                        }
                    }
                    if (state.loading) {
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
}
