package miko.ui.sources

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun SourcesScreen(
    model: SourcesScreenModel,
    onSourceClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()

    when {
        !state.isInitialized -> LoadingScreen(modifier = modifier)
        state.sources.isEmpty() -> EmptyScreen(
            stringRes = MR.strings.source_empty_screen,
            modifier = modifier,
        )
        else -> LazyColumn(modifier = modifier.fillMaxSize()) {
            items(state.sources, key = { it.id }) { source ->
                SourceRow(
                    source = source,
                    onClick = { onSourceClick(source.id) },
                )
            }
        }
    }
}

@Composable
private fun SourceRow(
    source: NovelCatalogueSource,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = source.name,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = source.lang.uppercase(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
