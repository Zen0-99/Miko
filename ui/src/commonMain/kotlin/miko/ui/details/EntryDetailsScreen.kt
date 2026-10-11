package miko.ui.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import miko.ui.carrier.BrowseCarrier
import miko.ui.carrier.RemoteItem
import miko.ui.components.ChangeCollectionSheet
import miko.ui.components.ChapterSettingsSheet
import miko.ui.components.MigrateSourceSheet
import miko.ui.components.ScanlatorFilterSheet
import miko.ui.download.DownloadManager
import miko.ui.download.DownloadState
import tachiyomi.core.common.preference.TriState
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.clickableNoIndication
import kotlin.math.floor
import kotlin.time.Instant

private const val HERO_HEIGHT = 380

/**
 * Port of Android's `NovelScreen`: full-bleed cover backdrop with scrims,
 * centered display title + meta row, the accent continue bar flanked by
 * favourite / mark-all toggle boxes, clickable genre text line, expandable
 * description, and read-state-aware chapter rows.
 */
@Composable
fun EntryDetailsScreen(
    model: EntryDetailsScreenModel,
    carrier: BrowseCarrier,
    onItemClick: (RemoteItem) -> Unit,
    onGenreClick: (String) -> Unit,
    onEditCollections: () -> Unit,
    onMigrated: (sourceId: Long, entryUrl: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()
    // Cover-derived accent — Android's rememberEntryAccentColor; it tints
    // the continue bar, favorite box, and the item carousel.
    val accentColor = miko.ui.cover.rememberEntryAccentColor(state.details?.thumbnailUrl)

    if (state.collectionSheetOpen) {
        ChangeCollectionSheet(
            collections = state.collections,
            initialSelection = state.entryCollectionIds,
            onDismissRequest = model::dismissCollectionSheet,
            onEditCollections = onEditCollections,
            onConfirm = model::confirmCollections,
        )
    }

    if (state.migrateSheetOpen) {
        MigrateSourceSheet(
            sources = state.migrateSources,
            onDismissRequest = model::dismissMigrateSheet,
            onSourceSelected = { source ->
                model.migrateTo(source.id, onMigrated)
            },
        )
    }

    // Migration in flight — Android's migrate screen shows a spinner while
    // the entry copies across.
    if (state.migrating) {
        LoadingScreen()
        return
    }

    // Android's NovelCoverDialog/MangaCoverDialog — tap the hero cover for
    // a fullscreen viewer; tap anywhere to dismiss.
    var coverExpanded by remember { mutableStateOf(false) }
    if (coverExpanded) {
        Dialog(
            onDismissRequest = { coverExpanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { coverExpanded = false },
            ) {
                AsyncImage(
                    model = state.details?.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    // Android's details swipe-refresh — reloads details + item list.
    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(state.detailsLoading, state.itemsLoading) {
        if (!state.detailsLoading && !state.itemsLoading) isRefreshing = false
    }

    // Rating feeds the hero's star; stat/alt-title noise is stripped
    // from the rendered description.
    val parsedDescription = remember(state.details?.description) {
        EntryDescriptionParser.parse(state.details?.description)
    }

    // Android's chapter-group pills — every item name must carry a
    // Vol/Book/… marker or the row hides (canonical volume grouping is
    // mikoDB-side, not yet ported).
    val chapterGroups = remember(state.items) {
        ChapterNameGroup.computeGroups(state.items.map { it.name })
    }
    val progressGroup = remember(chapterGroups, state.doneUrls) {
        state.items
            .filter { it.url !in state.doneUrls }
            .minByOrNull { it.number }
            ?.let { ChapterNameGroup.parse(it.name) }
            ?.takeIf { it in chapterGroups }
    }
    var groupOverride by remember(state.local?.id) { mutableStateOf<String?>(null) }
    val selectedGroup = groupOverride?.takeIf { it in chapterGroups }
        ?: progressGroup
        ?: chapterGroups.lastOrNull()
    val completedGroups = remember(chapterGroups, state.doneUrls) {
        if (chapterGroups.size <= 1) {
            emptySet()
        } else {
            state.items
                .groupBy { ChapterNameGroup.parse(it.name) }
                .filterKeys { it != null }
                .filterValues { chs -> chs.all { it.url in state.doneUrls } }
                .keys
                .filterNotNull()
                .toSet()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                model.refresh()
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    NovelHero(
                        coverUrl = state.details?.thumbnailUrl,
                        onCoverClick = { coverExpanded = true },
                        title = state.details?.title ?: state.local?.title ?: "",
                        metaItems = listOfNotNull(
                            state.details?.author,
                            state.details?.status?.let { statusLabel(it) },
                            state.sourceName.ifBlank { null },
                        ),
                        rating = parsedDescription.rating,
                    )
                }

                item {
                    ContinueRow(
                        favorite = state.favorite,
                        allRead = state.allItemsDone,
                        hasStarted = state.hasStarted,
                        // Android's displayMode flag — "Chapter 5" vs the
                        // source name on the continue label.
                        nextChapterName = state.nextItem?.let {
                            if (state.displayByNumber && it.number >= 0.0) {
                                "${itemPrefix(carrier)} ${formatItemNumber(it.number)}"
                            } else {
                                it.name
                            }
                        },
                        enabled = state.nextItem != null || state.allItemsDone,
                        onContinueClick = { state.nextItem?.let(onItemClick) },
                        onFavoriteClick = model::toggleFavorite,
                        onToggleDoneClick = model::toggleAllItemsDone,
                        accentOverride = accentColor,
                    )
                }

                state.details?.genre?.takeIf { it.isNotEmpty() }?.let { genres ->
                    item {
                        GenreRow(
                            genres = genres,
                            onGenreClick = onGenreClick,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }

                if (parsedDescription.description.isNotBlank()) {
                    item {
                        ExpandableDescription(
                            description = parsedDescription.description,
                        )
                    }
                }

                item {
                    // Android's EntryChapterHeader — count + a single filter icon
                    // opening the chapter-settings sheet (Filter/Sort/Display).
                    var settingsOpen by remember { mutableStateOf(false) }
                    var downloadMenuOpen by remember { mutableStateOf(false) }
                    Box {
                        ChapterListHeader(
                            count = state.items.size,
                            filtersActive = state.unreadFilter != TriState.DISABLED ||
                                state.bookmarkedFilter != TriState.DISABLED ||
                                state.fillermarkedFilter != TriState.DISABLED,
                            showActions = state.local != null,
                            onFilterClick = { settingsOpen = true },
                            // Android's toolbar action mode — count + close /
                            // select-all / invert while rows are ticked.
                            selectedCount = state.selectedUrls.size,
                            onCancelSelection = model::clearSelection,
                            onSelectAll = model::selectAll,
                            onInvertSelection = model::invertSelection,
                            // Android's toolbar download dropdown — manga and
                            // books only; shows streams can't file-download.
                            onDownloadMenuClick = if (
                                carrier != BrowseCarrier.SHOWS &&
                                state.local != null
                            ) {
                                { downloadMenuOpen = true }
                            } else {
                                null
                            },
                        )
                        // Android's EntryDownloadDropdownMenu — "Download
                        // next 1/5/10/25" plus the unread set.
                        DropdownMenu(
                            expanded = downloadMenuOpen,
                            onDismissRequest = { downloadMenuOpen = false },
                        ) {
                            listOf(1, 5, 10, 25).forEach { n ->
                                DropdownMenuItem(
                                    text = {
                                        Text("Download next $n")
                                    },
                                    onClick = {
                                        downloadMenuOpen = false
                                        model.downloadNext(n)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (carrier == BrowseCarrier.SHOWS) {
                                                AYMR.strings.download_unseen
                                            } else {
                                                MR.strings.download_unread
                                            },
                                        ),
                                    )
                                },
                                onClick = {
                                    downloadMenuOpen = false
                                    model.downloadNext(null)
                                },
                            )
                        }
                    }
                    if (settingsOpen) {
                        ChapterSettingsSheet(
                            carrier = carrier,
                            unreadFilter = state.unreadFilter,
                            bookmarkedFilter = state.bookmarkedFilter,
                            fillermarkedFilter = state.fillermarkedFilter,
                            sortMode = state.sortMode,
                            sortDescending = state.sortDescending,
                            displayByNumber = state.displayByNumber,
                            onDismissRequest = { settingsOpen = false },
                            onCycleUnread = model::cycleUnreadFilter,
                            onCycleBookmarked = model::cycleBookmarkedFilter,
                            onCycleFillermarked = model::cycleFillermarkedFilter,
                            onSortMode = model::setChapterSortMode,
                            onToggleSortDirection = model::toggleSortDirection,
                            onDisplayByNumber = model::setDisplayByNumber,
                            // Shows only — episodeFlags preview/summary bits.
                            showPreviews = state.showPreviews,
                            showSummaries = state.showSummaries,
                            onShowPreviews = if (
                                carrier == BrowseCarrier.SHOWS && state.local != null
                            ) {
                                model::setShowPreviews
                            } else {
                                null
                            },
                            onShowSummaries = if (
                                carrier == BrowseCarrier.SHOWS && state.local != null
                            ) {
                                model::setShowSummaries
                            } else {
                                null
                            },
                            // Manga only — novel/anime have no exclusion
                            // table, so the row hides like Android.
                            scanlatorFilterActive = state.excludedScanlators.isNotEmpty(),
                            onScanlatorFilterClick = if (
                                carrier == BrowseCarrier.MANGA && state.local != null
                            ) {
                                model::showScanlatorSheet
                            } else {
                                null
                            },
                            // Android's tab overflow — defaults live on
                            // materialized entries only.
                            onSetAsDefault = if (state.local != null) {
                                model::setFlagsAsDefault
                            } else {
                                null
                            },
                            onResetToDefault = if (state.local != null) {
                                model::resetFlagsToDefault
                            } else {
                                null
                            },
                        )
                    }
                    if (state.scanlatorSheetOpen) {
                        ScanlatorFilterSheet(
                            scanlators = state.scanlatorNames,
                            initialExcluded = state.excludedScanlators,
                            onDismissRequest = model::dismissScanlatorSheet,
                            onConfirm = model::confirmExcludedScanlators,
                        )
                    }
                }

                when {
                    state.itemsLoading -> item { LoadingScreen() }
                    state.items.isEmpty() -> item {
                        Text(
                            text = stringResource(MR.strings.no_results_found),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    else -> {
                        val items = if (chapterGroups.size > 1 && selectedGroup != null) {
                            state.displayItems.filter {
                                ChapterNameGroup.parse(it.name) == selectedGroup
                            }
                        } else {
                            state.displayItems
                        }
                        val doneUrls = state.doneUrls
                        val selecting = state.selectedUrls.isNotEmpty()
                        // Android's insertSeparators — "Missing N items"
                        // pseudo-rows where consecutive numbers gap >1
                        // (head check ascending only); negative numbers are
                        // unrecognized and never gap.
                        val rows = buildList<ItemRowEntry> {
                            var prev: RemoteItem? = null
                            items.forEach { item ->
                                val gap = if (prev == null) {
                                    if (state.sortDescending) {
                                        -1
                                    } else {
                                        floor(item.number).toInt() - 1
                                    }
                                } else {
                                    val lo = minOf(prev!!.number, item.number)
                                    val hi = maxOf(prev!!.number, item.number)
                                    if (lo < 0.0) {
                                        -1
                                    } else {
                                        floor(hi).toInt() - floor(lo).toInt() - 1
                                    }
                                }
                                if (gap > 0) {
                                    add(
                                        ItemRowEntry.MissingCount(
                                            "${prev?.url ?: "head"}-${item.url}",
                                            gap,
                                        ),
                                    )
                                }
                                add(ItemRowEntry.ChapterRow(item))
                                prev = item
                            }
                        }
                        // Android's details item list is a horizontal
                        // snap carousel of 0.75×screen cards (the Nuvio
                        // episode-card styling), not a vertical list —
                        // scrubber above when the row is ≥40 items. The
                        // accent comes from the cover's dominant edge
                        // color (EntryCoverColorExtractor).
                        item(key = "item-carousel") {
                            val carouselState = rememberLazyListState(
                                initialFirstVisibleItemIndex = rows
                                    .indexOfFirst {
                                        it is ItemRowEntry.ChapterRow &&
                                            it.item.url == state.nextItem?.url
                                    }
                                    .takeIf { it >= 0 } ?: 0,
                            )
                            Column {
                                ChapterGroupPillRow(
                                    groups = chapterGroups,
                                    selectedGroup = selectedGroup ?: "",
                                    completedGroups = completedGroups,
                                    onGroupSelected = { groupOverride = it },
                                    accentColor = accentColor,
                                )
                                ItemCarouselScrubber(
                                    listState = carouselState,
                                    itemCount = rows.size,
                                    accentColor = accentColor,
                                )
                                LazyRow(
                                    state = carouselState,
                                    flingBehavior = rememberSnapFlingBehavior(
                                        lazyListState = carouselState,
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement =
                                    Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(vertical = 8.dp),
                                ) {
                                    items(
                                        rows,
                                        key = {
                                            when (it) {
                                                is ItemRowEntry.ChapterRow -> it.item.url
                                                is ItemRowEntry.MissingCount -> it.key
                                            }
                                        },
                                    ) { row ->
                                        when (row) {
                                            is ItemRowEntry.MissingCount ->
                                                MissingCountCarouselCard(row.count)
                                            is ItemRowEntry.ChapterRow ->
                                                ItemCarouselCard(
                                                    item = row.item,
                                                    carrier = carrier,
                                                    state = state,
                                                    selecting = selecting,
                                                    accentColor = accentColor,
                                                    onItemClick = onItemClick,
                                                    onToggleSelect = model::toggleSelection,
                                                    onDownload = model::downloadItem,
                                                    onCancelDownload = model::cancelDownload,
                                                    onDeleteDownload = model::deleteDownload,
                                                )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Android's EntryBottomActionMenu — slides up while rows are selected.
        val selectedUrls = state.selectedUrls
        SelectionActionMenu(
            carrier = carrier,
            visible = selectedUrls.isNotEmpty(),
            anyUndone = selectedUrls.any { it !in state.doneUrls },
            anyDone = selectedUrls.any { it in state.doneUrls },
            anyUnbookmarked = selectedUrls.any { it !in state.bookmarkedUrls },
            allBookmarked = selectedUrls.isNotEmpty() &&
                selectedUrls.all { it in state.bookmarkedUrls },
            anyUnfillermarked = selectedUrls.any { it !in state.fillermarkedUrls },
            allFillermarked = selectedUrls.isNotEmpty() &&
                selectedUrls.all { it in state.fillermarkedUrls },
            singleSelected = selectedUrls.size == 1,
            anyDownloaded = selectedUrls.any { url ->
                state.items.firstOrNull { it.url == url }?.let {
                    DownloadManager.sanitizeName(it.name) in state.downloadedNames
                } == true
            },
            onDeleteDownloads = model::deleteSelectedDownloads,
            onMarkDone = { model.markSelected(done = true) },
            onMarkUndone = { model.markSelected(done = false) },
            onBookmark = { model.bookmarkSelected(bookmarked = true) },
            onUnbookmark = { model.bookmarkSelected(bookmarked = false) },
            onFillermark = { model.fillermarkSelected(fillermarked = true) },
            onUnfillermark = { model.fillermarkSelected(fillermarked = false) },
            onMarkPrevious = { model.markPreviousDone(selectedUrls.first()) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * Android's `EnhancedEntryInfoBox` (reduced): full-bleed cover, top scrim for
 * the app-bar icons, transparent→background bottom scrim, and the centered
 * display title + meta tail pinned to the bottom edge.
 */
@Composable
private fun NovelHero(
    coverUrl: String?,
    title: String,
    metaItems: List<String>,
    onCoverClick: () -> Unit,
    /** Parsed source rating — star glyph next to the meta line. */
    rating: String? = null,
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HERO_HEIGHT.dp),
    ) {
        AsyncImage(
            model = coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onCoverClick),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, backgroundColor),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title.ifBlank { stringResource(MR.strings.unknown_title) },
                style = MaterialTheme.typography.displaySmall.merge(
                    TextStyle(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.86f),
                            offset = Offset(0f, 2.5f),
                            blurRadius = 10f,
                        ),
                    ),
                ),
                color = Color.White,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (metaItems.isNotEmpty() || rating != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    if (metaItems.isNotEmpty()) {
                        Text(
                            text = metaItems.joinToString("  ·  "),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground
                                .copy(alpha = SECONDARY_ALPHA),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                    rating?.let {
                        // ImdbRatingLabel (STAR mark) — parsed source rating.
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground
                                .copy(alpha = SECONDARY_ALPHA),
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground
                                .copy(alpha = SECONDARY_ALPHA),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Android's `EntryContinueRow`: a 16dp-rounded accent bar (book icon + Start /
 * Continue / All up to date) flanked by 44dp rounded-square toggle boxes —
 * favourite left, mark-all-done right.
 */
@Composable
private fun ContinueRow(
    favorite: Boolean,
    allRead: Boolean,
    hasStarted: Boolean,
    nextChapterName: String?,
    enabled: Boolean,
    onContinueClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onToggleDoneClick: () -> Unit,
    accentOverride: Color? = null,
) {
    // The cover-derived accent replaces the theme primary once extracted —
    // Android's EntryCoverColorExtractor + rememberOnAccentColor.
    val accent = accentOverride ?: MaterialTheme.colorScheme.primary
    val onAccent = if (accentOverride != null) {
        if (accent.luminance() > 0.5f) Color.Black else Color.White
    } else {
        MaterialTheme.colorScheme.onPrimary
    }

    val label = when {
        allRead -> stringResource(AYMR.strings.fetching_overlay_all_up_to_date)
        nextChapterName != null && hasStarted ->
            stringResource(MR.strings.action_continue_named, nextChapterName)
        else -> stringResource(MR.strings.action_start)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleBoxAction(
            icon = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(
                if (favorite) MR.strings.in_library else MR.strings.add_to_library,
            ),
            active = favorite,
            activeColor = accent,
            onActiveColor = onAccent,
            onClick = onFavoriteClick,
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (enabled) {
                        accent
                    } else {
                        accent.copy(alpha = 0.15f)
                    },
                )
                .clickable(enabled = enabled, onClickLabel = null, onClick = onContinueClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val contentColor = if (enabled) onAccent else accent
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (allRead) {
                        Icons.Filled.CheckCircle
                    } else {
                        Icons.AutoMirrored.Filled.MenuBook
                    },
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(start = 6.dp),
                )
            }
        }
        ToggleBoxAction(
            icon = if (allRead) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
            contentDescription = stringResource(
                if (allRead) {
                    MR.strings.action_mark_all_as_unread
                } else {
                    MR.strings.action_mark_all_as_read
                },
            ),
            active = allRead,
            activeColor = accent,
            onActiveColor = onAccent,
            onClick = onToggleDoneClick,
        )
    }
}

@Composable
private fun ToggleBoxAction(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    activeColor: Color,
    onActiveColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(shape)
            .then(
                if (active) {
                    Modifier.background(activeColor)
                } else {
                    Modifier.border(
                        1.5.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        shape,
                    )
                },
            )
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) {
                onActiveColor
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Android's `EntryGenreRow`: genres as one centered line of accent-coloured,
 * individually clickable spans separated by muted dots; scrolls horizontally
 * when it overflows instead of ellipsizing.
 */
@Composable
private fun GenreRow(
    genres: List<String>,
    onGenreClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val genreColor = MaterialTheme.colorScheme.primary
    val separatorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)

    val text = remember(genres, genreColor, separatorColor, onGenreClick) {
        buildAnnotatedString {
            genres.forEachIndexed { index, genre ->
                if (index > 0) {
                    withStyle(SpanStyle(color = separatorColor)) {
                        append(" · ")
                    }
                }
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "genre",
                        linkInteractionListener = { onGenreClick(genre) },
                    ),
                ) {
                    withStyle(SpanStyle(color = genreColor)) {
                        append(genre)
                    }
                }
            }
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

/**
 * Android's `ExpandableEntryDescription` — "Description" header, bodyMedium
 * text clamped to 3 lines (overflow-detected, click only when overflowing),
 * and a right-aligned "Less" row when expanded.
 */
@Composable
private fun ExpandableDescription(
    description: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var isOverflowing by remember { mutableStateOf(false) }
    val descColor = MaterialTheme.colorScheme.onBackground.copy(alpha = SECONDARY_ALPHA)
    val accent = MaterialTheme.colorScheme.primary

    Column(modifier = modifier.padding(bottom = 8.dp)) {
        Text(
            text = stringResource(MR.strings.description),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(top = 12.dp, bottom = 4.dp)
                .padding(horizontal = 16.dp),
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clipToBounds()
                .then(
                    if (isOverflowing) {
                        Modifier.clickable { expanded = !expanded }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = description.replace(whitespaceLineRegex, "\n").trimEnd(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = descColor,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.fillMaxWidth(),
                    onTextLayout = { result ->
                        isOverflowing = if (expanded) {
                            result.lineCount > 3
                        } else {
                            result.hasVisualOverflow || result.lineCount > 3
                        }
                    },
                )
                if (expanded && isOverflowing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clickable { expanded = false },
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(MR.strings.manga_info_collapse),
                            style = MaterialTheme.typography.bodyMedium,
                            color = accent,
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

private val whitespaceLineRegex = Regex("\\s*\n\\s*")

/**
 * Android's `EntryChapterHeader`: item count plus the filter icon that opens
 * the chapter-settings sheet. Actions hide until the entry has a local row
 * to persist flags onto.
 */
@Composable
private fun ChapterListHeader(
    count: Int,
    filtersActive: Boolean,
    showActions: Boolean,
    onFilterClick: () -> Unit,
    selectedCount: Int = 0,
    onCancelSelection: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    onInvertSelection: () -> Unit = {},
    /** Android's toolbar download dropdown — non-null shows the icon. */
    onDownloadMenuClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
    ) {
        if (selectedCount > 0) {
            // Android's action mode — count title + close/select-all/invert.
            IconButton(onClick = onCancelSelection) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(MR.strings.action_cancel),
                )
            }
            Text(
                text = "$selectedCount",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            IconButton(onClick = onSelectAll) {
                Icon(
                    imageVector = Icons.Outlined.SelectAll,
                    contentDescription = stringResource(MR.strings.action_select_all),
                )
            }
            IconButton(onClick = onInvertSelection) {
                Icon(
                    imageVector = Icons.Outlined.FlipToBack,
                    contentDescription = stringResource(MR.strings.action_select_inverse),
                )
            }
            return@Row
        }
        Text(
            text = pluralStringResource(
                MR.plurals.manga_num_chapters,
                count = count,
                count,
            ),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (onDownloadMenuClick != null) {
            IconButton(onClick = onDownloadMenuClick) {
                Icon(
                    imageVector = Icons.Outlined.FileDownload,
                    contentDescription = stringResource(MR.strings.action_download),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showActions) {
            IconButton(onClick = onFilterClick) {
                Icon(
                    imageVector = Icons.Filled.FilterList,
                    contentDescription = null,
                    tint = if (filtersActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/**
 * Card dispatcher — manga/novel get the tinted-surface
 * [ChapterCarouselCard], shows the full-bleed [EpisodeStillCard]. Same
 * data flows (done/selected/next/download) drive both.
 */
@Composable
private fun ItemCarouselCard(
    item: RemoteItem,
    carrier: BrowseCarrier,
    state: EntryDetailsScreenModel.State,
    selecting: Boolean,
    accentColor: Color?,
    onItemClick: (RemoteItem) -> Unit,
    onToggleSelect: (String) -> Unit,
    onDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDeleteDownload: (String) -> Unit,
) {
    val done = item.url in state.doneUrls
    val canDownload = carrier != BrowseCarrier.SHOWS && state.local != null
    val downloadTrailing: (@Composable (Color) -> Unit)? = if (canDownload) {
        { tint ->
            ItemDownloadIndicator(
                tint = tint,
                downloadState = state.downloadStates[item.url],
                downloadProgress = state.downloadProgress[item.url] ?: 0f,
                downloaded = DownloadManager.sanitizeName(item.name) in state.downloadedNames,
                onDownloadClick = if (state.downloadStates[item.url] == null) {
                    { onDownload(item.url) }
                } else {
                    null
                },
                onCancelClick = if (
                    state.downloadStates[item.url] in
                    setOf(DownloadState.QUEUED, DownloadState.DOWNLOADING)
                ) {
                    { onCancelDownload(item.url) }
                } else {
                    null
                },
                onDeleteClick = { onDeleteDownload(item.url) },
            )
        }
    } else {
        null
    }
    val title = if (state.displayByNumber && item.number >= 0.0) {
        "${itemPrefix(carrier)} ${formatItemNumber(item.number)}"
    } else {
        item.name
    }
    val dateLabel = remember(item.dateUpload) {
        miko.ui.updates.dateLabel(
            Instant.fromEpochMilliseconds(item.dateUpload)
                .toLocalDateTime(TimeZone.currentSystemDefault()).date,
        )
    }
    val progressLabel = when (carrier) {
        BrowseCarrier.SHOWS -> state.progressSeconds[item.url]
            ?.let { (seen, _) -> formatEpisodeRuntime(seen * 1000) }
        BrowseCarrier.BOOKS -> state.progressPercents[item.url]
            ?.let { stringResource(MR.strings.novel_chapter_progress, it) }
        else -> state.progressPages[item.url]
            ?.let { stringResource(MR.strings.chapter_progress, it + 1) }
    }
    val click = {
        if (selecting) onToggleSelect(item.url) else onItemClick(item)
    }
    val longClick: () -> Unit = { onToggleSelect(item.url) }

    if (carrier == BrowseCarrier.SHOWS) {
        EpisodeStillCard(
            title = title,
            episodeString = if (item.number >= 0.0) {
                "Ep. ${formatItemNumber(item.number)}"
            } else {
                ""
            },
            date = dateLabel,
            watchProgress = if (done) null else progressLabel,
            progressFraction = state.progressSeconds[item.url]
                ?.let { (seen, total) -> if (total > 0) seen.toFloat() / total else null },
            scanlator = item.scanlator,
            summary = item.summary.takeIf { state.showSummaries },
            previewUrl = item.previewUrl.takeIf { state.showPreviews },
            seen = done,
            bookmark = item.url in state.bookmarkedUrls,
            selected = item.url in state.selectedUrls,
            isNext = item.url == state.nextItem?.url,
            onLongClick = if (state.local != null) longClick else ({}),
            onClick = click,
            accentColor = accentColor,
            trailing = downloadTrailing,
        )
    } else {
        ChapterCarouselCard(
            title = title,
            dateLabel = dateLabel,
            scanlator = item.scanlator?.takeIf { it.isNotBlank() },
            progress = if (done) null else progressLabel,
            read = done,
            bookmark = item.url in state.bookmarkedUrls,
            selected = item.url in state.selectedUrls,
            isNext = item.url == state.nextItem?.url,
            onLongClick = if (state.local != null) longClick else ({}),
            onClick = click,
            accentColor = accentColor,
            trailing = downloadTrailing,
        )
    }
}

/** Android's ChapterDownloadIndicator as a card trailing slot. */
@Composable
private fun ItemDownloadIndicator(
    tint: Color,
    downloadState: DownloadState?,
    downloadProgress: Float,
    downloaded: Boolean,
    onDownloadClick: (() -> Unit)?,
    onCancelClick: (() -> Unit)?,
    onDeleteClick: () -> Unit,
) {
    when {
        downloadState == DownloadState.DOWNLOADING -> {
            CircularProgressIndicator(
                progress = { downloadProgress },
                color = tint,
                modifier = Modifier
                    .size(16.dp)
                    .then(
                        if (onCancelClick != null) {
                            Modifier.clickableNoIndication(onClick = onCancelClick)
                        } else {
                            Modifier
                        },
                    ),
                strokeWidth = 2.dp,
            )
        }
        downloadState == DownloadState.QUEUED -> {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = "Queued for download",
                tint = tint,
                modifier = Modifier
                    .size(16.dp)
                    .then(
                        if (onCancelClick != null) {
                            Modifier.clickableNoIndication(onClick = onCancelClick)
                        } else {
                            Modifier
                        },
                    ),
            )
        }
        downloadState == DownloadState.ERROR -> {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = "Download failed",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
        }
        downloaded -> {
            Icon(
                imageVector = Icons.Filled.DownloadDone,
                contentDescription = "Downloaded",
                tint = tint,
                modifier = Modifier
                    .size(18.dp)
                    .clickableNoIndication(onClick = onDeleteClick),
            )
        }
        onDownloadClick != null -> {
            Icon(
                imageVector = Icons.Outlined.FileDownload,
                contentDescription = "Download",
                tint = tint,
                modifier = Modifier
                    .size(18.dp)
                    .clickableNoIndication(onClick = onDownloadClick),
            )
        }
    }
}

@Composable
private fun statusLabel(status: Int): String = stringResource(
    when (status) {
        1 -> AYMR.strings.status_ongoing
        2 -> AYMR.strings.status_completed
        3 -> AYMR.strings.status_licensed
        4 -> AYMR.strings.status_publishing_finished
        5 -> AYMR.strings.status_cancelled
        6 -> AYMR.strings.status_on_hiatus
        else -> AYMR.strings.status_unknown
    },
)

private fun itemPrefix(carrier: BrowseCarrier): String = when (carrier) {
    BrowseCarrier.SHOWS -> "Ep."
    else -> "Ch."
}

/** "1" for whole numbers, "2.5" otherwise — Android's formatChapterNumber. */
private fun formatItemNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

/**
 * Android's `EntryBottomActionMenu`: a bottom-anchored row of icon-over-label
 * actions shown while the item selection is non-empty. Each action appears
 * only when it would do something (any-unbookmarked shows Bookmark, and so
 * on), matching the upstream visibility gates.
 */
@Composable
private fun SelectionActionMenu(
    carrier: BrowseCarrier,
    visible: Boolean,
    anyUndone: Boolean,
    anyDone: Boolean,
    anyUnbookmarked: Boolean,
    allBookmarked: Boolean,
    anyUnfillermarked: Boolean,
    allFillermarked: Boolean,
    singleSelected: Boolean,
    anyDownloaded: Boolean,
    onDeleteDownloads: () -> Unit,
    onMarkDone: () -> Unit,
    onMarkUndone: () -> Unit,
    onBookmark: () -> Unit,
    onUnbookmark: () -> Unit,
    onFillermark: () -> Unit,
    onUnfillermark: () -> Unit,
    onMarkPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isShows = carrier == BrowseCarrier.SHOWS
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(expandFrom = Alignment.Bottom),
        exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large.copy(
                bottomStart = CornerSize(0.dp),
                bottomEnd = CornerSize(0.dp),
            ),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            ) {
                if (anyUnbookmarked) {
                    SelectionAction(
                        label = stringResource(
                            if (isShows) {
                                AYMR.strings.action_bookmark_episode
                            } else {
                                MR.strings.action_bookmark
                            },
                        ),
                        icon = Icons.Outlined.BookmarkBorder,
                        onClick = onBookmark,
                    )
                }
                if (allBookmarked) {
                    SelectionAction(
                        label = stringResource(
                            if (isShows) {
                                AYMR.strings.action_remove_bookmark_episode
                            } else {
                                MR.strings.action_remove_bookmark
                            },
                        ),
                        icon = Icons.Filled.BookmarkRemove,
                        onClick = onUnbookmark,
                    )
                }
                if (isShows && anyUnfillermarked) {
                    SelectionAction(
                        label = stringResource(AYMR.strings.action_fillermark_episode),
                        icon = Icons.Filled.Label,
                        onClick = onFillermark,
                    )
                }
                if (isShows && allFillermarked) {
                    SelectionAction(
                        label = stringResource(AYMR.strings.action_remove_fillermark_episode),
                        icon = Icons.Outlined.Label,
                        onClick = onUnfillermark,
                    )
                }
                if (anyUndone) {
                    SelectionAction(
                        label = stringResource(
                            if (isShows) {
                                AYMR.strings.action_mark_as_seen
                            } else {
                                MR.strings.action_mark_as_read
                            },
                        ),
                        icon = Icons.Filled.Visibility,
                        onClick = onMarkDone,
                    )
                }
                if (anyDone) {
                    SelectionAction(
                        label = stringResource(
                            if (isShows) {
                                AYMR.strings.action_mark_as_unseen
                            } else {
                                MR.strings.action_mark_as_unread
                            },
                        ),
                        icon = Icons.Outlined.VisibilityOff,
                        onClick = onMarkUndone,
                    )
                }
                if (singleSelected) {
                    SelectionAction(
                        label = stringResource(
                            if (isShows) {
                                AYMR.strings.action_mark_previous_as_seen
                            } else {
                                MR.strings.action_mark_previous_as_read
                            },
                        ),
                        icon = Icons.Filled.CheckCircle,
                        onClick = onMarkPrevious,
                    )
                }
                // Android's delete-download action — appears whenever any
                // selected item is downloaded (shows excluded upstream).
                if (!isShows && anyDownloaded) {
                    SelectionAction(
                        label = stringResource(MR.strings.action_delete),
                        icon = Icons.Outlined.Delete,
                        onClick = onDeleteDownloads,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.SelectionAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Row model for the items list — chapters/episodes or a missing-count separator. */
private sealed interface ItemRowEntry {
    data class ChapterRow(val item: RemoteItem) : ItemRowEntry
    data class MissingCount(val key: String, val count: Int) : ItemRowEntry
}
