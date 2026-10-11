package miko.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Badge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import miko.ui.components.CoverGridItem
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Android's `NovelLibraryCompactGrid` — cover grid with `EntryCompactGridItem`
 * cells (title overlay + UnviewedBadge top-start).
 */
@Composable
fun LibraryScreen(
    model: LibraryScreenModel,
    onNovelClick: (LibraryNovel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()

    when {
        !state.initialized -> LoadingScreen(modifier = modifier)
        state.entries.isEmpty() -> EmptyScreen(
            stringRes = MR.strings.information_empty_library,
            modifier = modifier,
        )
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 120.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(state.entries, key = { it.id }) { entry ->
                CoverGridItem(
                    title = entry.novel.title,
                    coverUrl = entry.novel.thumbnailUrl,
                    onClick = { onNovelClick(entry) },
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
