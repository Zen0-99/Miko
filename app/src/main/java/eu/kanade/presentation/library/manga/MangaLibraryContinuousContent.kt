package eu.kanade.presentation.library.manga

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.presentation.library.components.EntryCompactGridItem
import eu.kanade.presentation.library.components.EntryComfortableGridItem
import eu.kanade.presentation.library.components.EntryListItem
import eu.kanade.presentation.library.components.DownloadsBadge
import eu.kanade.presentation.library.components.UnviewedBadge
import eu.kanade.presentation.library.components.LanguageBadge
import eu.kanade.presentation.library.components.PinnedBadge
import eu.kanade.presentation.library.components.CollectionHeaderRow
import eu.kanade.presentation.library.components.CommonEntryItemDefaults
import eu.kanade.presentation.library.manga.buildListSubtitle
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.presentation.core.components.material.PullRefresh
import kotlin.time.Duration.Companion.seconds

/**
 * Continuous scroll library content — all collections in a single scroll
 * with section headers, as an alternative to the tabbed pager.
 * Uses LazyVerticalGrid so display modes (grid/list) work properly.
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
    sortLabel: String? = null,
    sortDescending: Boolean? = null,
    onSortClick: () -> Unit = {},
    getSortLabelForCollection: (Collection) -> String? = { null },
    getSortDescendingForCollection: (Collection) -> Boolean? = { null },
    onSortClickForCollection: (Collection) -> Unit = {},
    showListAuthor: Boolean = false,
    showListStatus: Boolean = false,
    isEntryLocked: ((Long) -> Boolean)? = null,
) {
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val notSelectionMode = selection.isEmpty()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val columns by remember(isLandscape) { getColumnsForOrientation(isLandscape) }

    // Use the display mode from the first collection (global setting)
    val displayMode by getDisplayMode(0)

    // For list mode, use 1 column; for grid modes, use the user's column preference
    val isListMode = displayMode == LibraryDisplayMode.List
    val effectiveColumns = when (displayMode) {
        LibraryDisplayMode.List -> 1
        else -> if (columns > 0) columns else 0 // 0 = auto-fit
    }

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
        LazyVerticalGrid(
            columns = if (effectiveColumns > 0) GridCells.Fixed(effectiveColumns)
                      else GridCells.Adaptive(128.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                    end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
                    bottom = contentPadding.calculateBottomPadding(),
                ),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                start = if (isListMode) 0.dp else 8.dp,
                end = if (isListMode) 0.dp else 8.dp,
                bottom = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (isListMode) 0.dp else CommonEntryItemDefaults.GridVerticalSpacer),
            horizontalArrangement = Arrangement.spacedBy(if (isListMode) 0.dp else CommonEntryItemDefaults.GridHorizontalSpacer),
        ) {
            collections.forEachIndexed { index, collection ->
                val items = getLibraryForPage(index)
                if (items.isEmpty()) return@forEachIndexed

                // Header — spans all columns, full-width.
                // Bottom padding compensates for the verticalArrangement.spacedBy
                // so the total gap (padding + spacedBy) matches tabbed mode's
                // 8dp top contentPadding: grid 4dp + 4dp = 8dp, list 8dp + 0dp = 8dp.
                item(key = "header_${collection.id}", span = { GridItemSpan(maxLineSpan) }) {
                    CollectionHeaderRow(
                        title = collection.visualName,
                        itemCount = getNumberOfMangaForCollection(collection),
                        sortLabel = getSortLabelForCollection(collection) ?: sortLabel,
                        sortDescending = getSortDescendingForCollection(collection) ?: sortDescending,
                        onSortClick = { onSortClickForCollection(collection) },
                        modifier = Modifier
                            .padding(horizontal = if (isListMode) 16.dp else 8.dp)
                            .padding(bottom = if (isListMode) 8.dp else 4.dp),
                    )
                }

                // Items — rendered using the proper display mode
                items(
                    items = items,
                    key = { "item_${it.libraryManga.manga.id}" },
                    contentType = { "manga_library_continuous_item" },
                ) { libraryItem ->
                    val manga = libraryItem.libraryManga.manga
                    val isSelected = selection.fastAny { it.id == libraryItem.libraryManga.id }
                    val coverData = MangaCover(
                        mangaId = manga.id,
                        sourceId = manga.source,
                        isMangaFavorite = manga.favorite,
                        url = manga.thumbnailUrl,
                        lastModified = manga.coverLastModified,
                    )
                    val coverBadgeStart: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                        DownloadsBadge(count = libraryItem.downloadCount)
                        UnviewedBadge(count = libraryItem.unreadCount)
                    }
                    val coverBadgeEnd: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                        LanguageBadge(
                            isLocal = libraryItem.isLocal,
                            sourceLanguage = libraryItem.sourceLanguage,
                        )
                    }
                    val topEndBadge: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? =
                        if (libraryItem.pinned) { { PinnedBadge() } } else null

                    when (displayMode) {
                        LibraryDisplayMode.List -> {
                            val subtitle = buildListSubtitle(manga, showListAuthor, showListStatus)
                            EntryListItem(
                                isSelected = isSelected,
                                title = manga.title,
                                coverData = coverData,
                                badge = {
                                    DownloadsBadge(count = libraryItem.downloadCount)
                                    UnviewedBadge(count = libraryItem.unreadCount)
                                    LanguageBadge(
                                        isLocal = libraryItem.isLocal,
                                        sourceLanguage = libraryItem.sourceLanguage,
                                    )
                                },
                                onLongClick = { onToggleRangeSelection(libraryItem.libraryManga) },
                                onClick = { onClickManga(libraryItem.libraryManga) },
                                onClickContinueViewing = if (onContinueReadingClicked != null &&
                                    isEntryLocked?.invoke(manga.id) != true
                                ) {
                                    { onContinueReadingClicked(libraryItem.libraryManga) }
                                } else null,
                                subtitle = subtitle,
                            )
                        }
                        LibraryDisplayMode.CompactGrid -> {
                            EntryCompactGridItem(
                                isSelected = isSelected,
                                title = manga.title,
                                coverData = coverData,
                                coverBadgeStart = coverBadgeStart,
                                coverBadgeEnd = coverBadgeEnd,
                                topEndBadge = topEndBadge,
                                onClick = { onClickManga(libraryItem.libraryManga) },
                                onLongClick = { onToggleRangeSelection(libraryItem.libraryManga) },
                            )
                        }
                        LibraryDisplayMode.CoverOnlyGrid -> {
                            EntryCompactGridItem(
                                isSelected = isSelected,
                                title = null,
                                coverData = coverData,
                                coverBadgeStart = coverBadgeStart,
                                coverBadgeEnd = coverBadgeEnd,
                                topEndBadge = topEndBadge,
                                onClick = { onClickManga(libraryItem.libraryManga) },
                                onLongClick = { onToggleRangeSelection(libraryItem.libraryManga) },
                            )
                        }
                        LibraryDisplayMode.ComfortableGrid -> {
                            val authorSubtitle = if (showListAuthor) {
                                val authorArtist = if (manga.author == manga.artist || manga.artist.isNullOrBlank()) {
                                    manga.author?.trim()?.takeIf { it.isNotBlank() }
                                } else {
                                    listOfNotNull(
                                        manga.author?.trim()?.takeIf { it.isNotBlank() },
                                        manga.artist?.trim()?.takeIf { it.isNotBlank() },
                                    ).joinToString(", ").takeIf { it.isNotBlank() }
                                }
                                authorArtist
                            } else null
                            EntryComfortableGridItem(
                                isSelected = isSelected,
                                title = manga.title,
                                coverData = coverData,
                                coverBadgeStart = coverBadgeStart,
                                coverBadgeEnd = coverBadgeEnd,
                                topEndBadge = topEndBadge,
                                onClick = { onClickManga(libraryItem.libraryManga) },
                                onLongClick = { onToggleRangeSelection(libraryItem.libraryManga) },
                                subtitle = authorSubtitle,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val Collection.visualName: String
    get() = if (name.isBlank()) "Default" else name
