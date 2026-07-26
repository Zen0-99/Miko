package eu.kanade.presentation.library.manga

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.presentation.core.components.material.PullRefresh
import kotlin.time.Duration.Companion.seconds

/**
 * Continuous scroll library content — all collections in a single scroll
 * with section headers, as an alternative to the tabbed pager.
 */
@Composable
fun MangaLibraryContinuousContent(
    collections: List<Collection>,
    searchQuery: String?,
    selection: List<LibraryManga>,
    contentPadding: PaddingValues,
    hasActiveFilters: Boolean,
    onMangaClicked: (Long) -> Unit,
    onContinueReadingClicked: ((LibraryManga) -> Unit)?,
    onToggleSelection: (LibraryManga) -> Unit,
    onToggleRangeSelection: (LibraryManga) -> Unit,
    onRefresh: (Collection?) -> Boolean,
    onGlobalSearchClicked: () -> Unit,
    getNumberOfMangaForCollection: (Collection) -> Int?,
    getDisplayMode: (Int) -> PreferenceMutableState<LibraryDisplayMode>,
    getColumnsForOrientation: (Boolean) -> PreferenceMutableState<Int>,
    getLibraryForPage: (Int) -> List<MangaLibraryItem>,
) {
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val notSelectionMode = selection.isEmpty()

    val onClickManga = { manga: LibraryManga ->
        if (notSelectionMode) {
            onMangaClicked(manga.manga.id)
        } else {
            onToggleSelection(manga)
        }
    }

    PullRefresh(
        refreshing = isRefreshing,
        onRefresh = {
            val started = onRefresh(null)
            if (!started) return@PullRefresh
            scope.launch {
                isRefreshing = true
                delay(1.seconds)
                isRefreshing = false
            }
        },
        enabled = notSelectionMode,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = contentPadding.calculateStartPadding(
                        androidx.compose.ui.platform.LocalLayoutDirection.current,
                    ),
                    end = contentPadding.calculateEndPadding(
                        androidx.compose.ui.platform.LocalLayoutDirection.current,
                    ),
                    bottom = contentPadding.calculateBottomPadding(),
                ),
            contentPadding = PaddingValues(top = contentPadding.calculateTopPadding()),
        ) {
            collections.forEachIndexed { index, collection ->
                val items = getLibraryForPage(index)
                if (items.isEmpty()) return@forEachIndexed

                item(key = "header_${collection.id}") {
                    Text(
                        text = buildString {
                            append(collection.visualName)
                            val count = getNumberOfMangaForCollection(collection)
                            if (count != null) append(" ($count)")
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(
                            horizontal = 16.dp,
                            vertical = 8.dp,
                        ),
                    )
                }

                items(
                    items = items,
                    key = { "item_${it.libraryManga.manga.id}" },
                ) { item ->
                    MangaLibraryItemRow(
                        item = item,
                        isSelected = selection.any { it.manga.id == item.libraryManga.manga.id },
                        onClick = { onClickManga(item.libraryManga) },
                        onLongClick = { onToggleRangeSelection(item.libraryManga) },
                        onClickContinueReading = if (onContinueReadingClicked != null) {
                            { onContinueReadingClicked(item.libraryManga) }
                        } else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun MangaLibraryItemRow(
    item: MangaLibraryItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClickContinueReading: (() -> Unit)?,
) {
    // Delegate to the existing MangaLibraryGridItem or MangaLibraryListItem
    // For simplicity in the skeleton, we use a basic row
    androidx.compose.material3.Surface(
        selected = isSelected,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = item.libraryManga.manga.title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(8.dp),
        )
    }
}

private val Collection.visualName: String
    get() = if (name.isBlank()) "Default" else name
