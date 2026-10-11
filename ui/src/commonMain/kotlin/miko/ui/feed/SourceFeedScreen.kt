package miko.ui.feed

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import miko.ui.carrier.FeedEntry
import miko.ui.components.CoverComfortableItem
import miko.ui.components.CoverGridItem
import miko.ui.components.CoverListItem
import miko.ui.feed.SourceFeedScreenModel.Listing
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Port of Android's `BrowseNovelSourceScreen` content: a FilterChip row
 * (Popular / Latest / Filter) over a paged feed rendered in the user's
 * display mode (compact grid / comfortable grid / list). Search lives in
 * the top bar (Android's `SearchToolbar`), not on this screen.
 */
@Composable
fun SourceFeedScreen(
    model: SourceFeedScreenModel,
    onEntryClick: (FeedEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        // Android's chip row — horizontal scroll, small padding/spacing,
        // divider beneath.
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val searching = state.query.isNotBlank()
            FilterChip(
                selected = !searching && state.listing == Listing.Popular,
                onClick = { model.setListing(Listing.Popular) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
                label = { Text(text = stringResource(MR.strings.popular)) },
            )
            if (state.supportsLatest) {
                FilterChip(
                    selected = !searching && state.listing == Listing.Latest,
                    onClick = { model.setListing(Listing.Latest) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.NewReleases,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                    label = { Text(text = stringResource(MR.strings.latest)) },
                )
            }
            if (state.hasFilters) {
                FilterChip(
                    selected = false,
                    onClick = {},
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.FilterList,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                    label = { Text(text = stringResource(MR.strings.action_filter)) },
                )
            }
        }
        HorizontalDivider()

        when {
            state.initialLoading -> LoadingScreen()
            state.items.isEmpty() && state.error != null -> FeedError(
                message = state.error ?: "",
                onRetry = model::retry,
            )
            state.items.isEmpty() -> EmptyScreen(
                stringRes = MR.strings.no_results_found,
            )
            else -> when (state.displayMode) {
                LibraryDisplayMode.List -> FeedList(model = model, onEntryClick = onEntryClick)
                else -> FeedGrid(model = model, onEntryClick = onEntryClick)
            }
        }
    }
}

@Composable
private fun FeedGrid(
    model: SourceFeedScreenModel,
    onEntryClick: (FeedEntry) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val compact = state.displayMode != LibraryDisplayMode.ComfortableGrid

    // Infinite scroll: kick the next page when the tail approaches.
    LaunchedEffect(gridState, model) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .map { lastVisible ->
                lastVisible != null &&
                    state.items.isNotEmpty() &&
                    lastVisible >= state.items.lastIndex - 6
            }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) model.loadNext() }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 120.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(state.items, key = { it.url }) { entry ->
            if (compact) {
                CoverGridItem(
                    title = entry.title,
                    coverUrl = entry.coverUrl,
                    onClick = { onEntryClick(entry) },
                )
            } else {
                CoverComfortableItem(
                    title = entry.title,
                    coverUrl = entry.coverUrl,
                    onClick = { onEntryClick(entry) },
                )
            }
        }
        feedTailItems(state.isLoading, state.error, model::retry)
    }
}

@Composable
private fun FeedList(
    model: SourceFeedScreenModel,
    onEntryClick: (FeedEntry) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(listState, model) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .map { lastVisible ->
                lastVisible != null &&
                    state.items.isNotEmpty() &&
                    lastVisible >= state.items.lastIndex - 6
            }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) model.loadNext() }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(state.items, key = { it.url }) { entry ->
            CoverListItem(
                title = entry.title,
                coverUrl = entry.coverUrl,
                subtitle = entry.subtitle,
                onClick = { onEntryClick(entry) },
            )
        }
        item {
            when {
                state.isLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                state.error != null -> FeedError(
                    message = state.error ?: "",
                    onRetry = model::retry,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.feedTailItems(
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
) {
    if (isLoading) {
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
    if (!isLoading && error != null) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            FeedError(message = error, onRetry = onRetry)
        }
    }
}

@Composable
private fun FeedError(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(MR.strings.action_retry))
        }
    }
}
