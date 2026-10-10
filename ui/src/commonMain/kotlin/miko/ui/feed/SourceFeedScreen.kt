package miko.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import miko.ui.feed.SourceFeedScreenModel.Listing
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun SourceFeedScreen(
    model: SourceFeedScreenModel,
    onNovelClick: (SNovel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

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

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = model::setQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(MR.strings.action_search_hint)) },
            singleLine = true,
        )

        if (state.supportsLatest) {
            SecondaryTabRow(
                selectedTabIndex = state.listing.ordinal,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Tab(
                    selected = state.listing == Listing.Popular,
                    onClick = { model.setListing(Listing.Popular) },
                    text = { Text(stringResource(MR.strings.popular)) },
                )
                Tab(
                    selected = state.listing == Listing.Latest,
                    onClick = { model.setListing(Listing.Latest) },
                    text = { Text(stringResource(MR.strings.latest)) },
                )
            }
        }

        when {
            state.initialLoading -> LoadingScreen()
            state.items.isEmpty() && state.error != null -> FeedError(
                message = state.error ?: "",
                onRetry = model::retry,
            )
            state.items.isEmpty() -> EmptyScreen(
                stringRes = MR.strings.no_results_found,
            )
            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 120.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items, key = { it.url }) { novel ->
                    NovelGridItem(novel = novel, onClick = { onNovelClick(novel) })
                }
                if (state.isLoading) {
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
                if (!state.isLoading && state.error != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        FeedError(message = state.error ?: "", onRetry = model::retry)
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelGridItem(
    novel: SNovel,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = novel.thumbnail_url,
            contentDescription = novel.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f),
            contentScale = ContentScale.Crop,
        )
        Text(
            text = novel.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp),
        )
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
