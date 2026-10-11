package miko.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.SNovel
import tachiyomi.domain.extension.novel.langDisplayName
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.secondaryItemAlpha

/**
 * Port of the Android `GlobalNovelSearchScreen` — one collapsible section
 * per catalogue source; each section header shows live state (spinner /
 * "No results") and holds a horizontal row of cover cards.
 */
@Composable
fun GlobalNovelSearchScreen(
    model: GlobalNovelSearchScreenModel,
    onSourceClick: (Long) -> Unit,
    onNovelClick: (Long, String) -> Unit,
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
                        onClickSource = { onSourceClick(source.id) },
                        onClickItem = { novel -> onNovelClick(source.id, novel.url) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GlobalSearchResultItem(
    source: NovelCatalogueSource,
    result: GlobalNovelSearchScreenModel.SearchItemResult,
    onClickSource: () -> Unit,
    onClickItem: (SNovel) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClickSource)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = langDisplayName(source.lang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (result) {
                is GlobalNovelSearchScreenModel.SearchItemResult.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                is GlobalNovelSearchScreenModel.SearchItemResult.Success -> {
                    if (result.novels.isEmpty()) {
                        Text(
                            text = stringResource(MR.strings.no_results_found),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is GlobalNovelSearchScreenModel.SearchItemResult.Error -> {
                    Text(
                        text = result.message ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (result is GlobalNovelSearchScreenModel.SearchItemResult.Success && result.novels.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                items(result.novels, key = { it.url }) { novel ->
                    GlobalSearchNovelCard(novel = novel, onClick = { onClickItem(novel) })
                }
            }
        }
    }
}

@Composable
private fun GlobalSearchNovelCard(
    novel: SNovel,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(112.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = novel.thumbnail_url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(MaterialTheme.shapes.medium),
        )
        Text(
            text = novel.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .secondaryItemAlpha()
                .padding(top = 4.dp),
        )
    }
}
