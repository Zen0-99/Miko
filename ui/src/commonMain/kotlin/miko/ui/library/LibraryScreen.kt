package miko.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Badge
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import miko.ui.carrier.BrowseCarrier
import miko.ui.components.CoverGridItem
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Android's library compact grid — cover grid with EntryCompactGridItem
 * cells (title overlay + UnviewedBadge top-start) for the active carrier.
 */
@Composable
fun LibraryScreen(
    model: LibraryScreenModel,
    carrierFlow: StateFlow<BrowseCarrier>,
    onCarrierChange: (BrowseCarrier) -> Unit,
    onEntryClick: (LibraryScreenModel.LibraryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val carrier by carrierFlow.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                BrowseCarrier.SHOWS to AYMR.strings.carrier_shows_library,
                BrowseCarrier.MANGA to AYMR.strings.carrier_manga_library,
                BrowseCarrier.BOOKS to AYMR.strings.carrier_books_library,
            ).forEach { (option, labelRes) ->
                FilterChip(
                    selected = carrier == option,
                    onClick = { onCarrierChange(option) },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }

        when {
            !state.initialized -> LoadingScreen(modifier = Modifier.fillMaxSize())
            state.entries.isEmpty() -> EmptyScreen(
                stringRes = MR.strings.information_empty_library,
                modifier = Modifier.fillMaxSize(),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.entries, key = { it.id }) { entry ->
                    CoverGridItem(
                        title = if (state.showTitle) entry.title else null,
                        coverUrl = entry.coverUrl,
                        onClick = { onEntryClick(entry) },
                        topStartBadge = if (entry.unreadCount > 0) {
                            { Badge { Text("${entry.unreadCount}") } }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}
