package miko.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import miko.ui.carrier.CarrierSource
import miko.ui.carrier.FeedEntry
import miko.ui.components.CoverComfortableItem
import tachiyomi.domain.extension.novel.langDisplayName
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Port of the Android `GlobalNovelSearchScreen` — one section per catalogue
 * source with `GlobalSearchResultItem` headers (title + inline "No results",
 * lang subtitle, spinner / arrow) and a horizontal `GlobalNovelSearchCardRow`
 * of comfortable cover cards.
 */
@Composable
fun GlobalSearchScreen(
    model: GlobalSearchScreenModel,
    onSourceClick: (Long, String) -> Unit,
    onEntryClick: (Long, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()

    when {
        !state.initialized -> LoadingScreen(modifier = modifier)
        state.items.isEmpty() -> EmptyScreen(
            stringRes = MR.strings.no_results_found,
            modifier = modifier,
        )
        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            state.items.forEach { (source, result) ->
                item(key = "source-${source.id}") {
                    GlobalSearchResultItem(
                        source = source,
                        result = result,
                        onClickSource = { onSourceClick(source.id, source.name) },
                        onClickItem = { entry -> onEntryClick(source.id, entry.url, entry.title) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GlobalSearchResultItem(
    source: CarrierSource,
    result: GlobalSearchScreenModel.SearchItemResult,
    onClickSource: () -> Unit,
    onClickItem: (FeedEntry) -> Unit,
) {
    val isLoading = result is GlobalSearchScreenModel.SearchItemResult.Loading
    val hasResults = result !is GlobalSearchScreenModel.SearchItemResult.Success ||
        result.entries.isNotEmpty()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, end = 4.dp)
                .fillMaxWidth()
                .clickable(
                    enabled = !isLoading && hasResults,
                    onClick = onClickSource,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = source.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!isLoading && !hasResults) {
                        Text(
                            text = "• " + stringResource(MR.strings.no_results_found),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
                Text(
                    text = langDisplayName(source.lang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                hasResults && result !is GlobalSearchScreenModel.SearchItemResult.Error -> {
                    IconButton(onClick = onClickSource) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                        )
                    }
                }
            }
        }

        when (result) {
            is GlobalSearchScreenModel.SearchItemResult.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.Center),
                        strokeWidth = 2.dp,
                    )
                }
            }
            is GlobalSearchScreenModel.SearchItemResult.Error -> {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(imageVector = Icons.Outlined.Error, contentDescription = null)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = result.message ?: stringResource(MR.strings.unknown_error),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            is GlobalSearchScreenModel.SearchItemResult.Success -> {
                if (result.entries.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(result.entries, key = { it.url }) { entry ->
                            Box(modifier = Modifier.width(96.dp)) {
                                CoverComfortableItem(
                                    title = entry.title,
                                    coverUrl = entry.coverUrl,
                                    titleMaxLines = 3,
                                    onClick = { onClickItem(entry) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
