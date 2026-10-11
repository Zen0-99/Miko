package miko.ui.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Label
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import miko.ui.carrier.BrowseCarrier
import miko.ui.carrier.ItemSwipeAction
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

                state.details?.description?.takeIf { it.isNotBlank() }?.let { description ->
                    item {
                        ExpandableDescription(description = description)
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
                        val items = state.displayItems
                        val doneUrls = state.doneUrls
                        val selecting = state.selectedUrls.isNotEmpty()
                        items(items, key = { it.url }) { item ->
                            val itemDone = item.url in doneUrls
                            val selected = item.url in state.selectedUrls
                            val canDownload = carrier != BrowseCarrier.SHOWS &&
                                state.local != null
                            // Android's chapter/episode swipe actions —
                            // the reveal triggers, then the row snaps back
                            // (confirmValueChange returns false, never
                            // Settled). Disabled in selection mode.
                            val swipeEnabled = !selecting && state.local != null
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    val action = when (value) {
                                        SwipeToDismissBoxValue.StartToEnd -> state.swipeStart
                                        SwipeToDismissBoxValue.EndToStart -> state.swipeEnd
                                        else -> ItemSwipeAction.Disabled
                                    }
                                    if (swipeEnabled && action != ItemSwipeAction.Disabled) {
                                        model.performItemSwipe(item.url, action)
                                    }
                                    false
                                },
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = swipeEnabled &&
                                    state.swipeStart != ItemSwipeAction.Disabled,
                                enableDismissFromEndToStart = swipeEnabled &&
                                    state.swipeEnd != ItemSwipeAction.Disabled,
                                backgroundContent = {
                                    val action = when (dismissState.dismissDirection) {
                                        SwipeToDismissBoxValue.StartToEnd -> state.swipeStart
                                        else -> state.swipeEnd
                                    }
                                    SwipeActionBackground(
                                        action = action,
                                        towardStart = dismissState.dismissDirection ==
                                            SwipeToDismissBoxValue.StartToEnd,
                                    )
                                },
                            ) {
                                Column {
                                    ItemRow(
                                        item = item,
                                        numberPrefix = itemPrefix(carrier),
                                        done = itemDone,
                                        displayByNumber = state.displayByNumber,
                                        bookmarked = item.url in state.bookmarkedUrls,
                                        selected = selected,
                                        showPreview = state.showPreviews,
                                        showSummary = state.showSummaries,
                                        downloadState = if (canDownload) {
                                            state.downloadStates[item.url]
                                        } else {
                                            null
                                        },
                                        downloadProgress =
                                        state.downloadProgress[item.url] ?: 0f,
                                        downloaded = canDownload &&
                                            DownloadManager.sanitizeName(item.name) in
                                            state.downloadedNames,
                                        onDownloadClick = if (
                                            canDownload &&
                                            state.downloadStates[item.url] == null
                                        ) {
                                            { model.downloadItem(item.url) }
                                        } else {
                                            null
                                        },
                                        onCancelClick = if (
                                            canDownload &&
                                            state.downloadStates[item.url] in
                                            setOf(
                                                DownloadState.QUEUED,
                                                DownloadState.DOWNLOADING,
                                            )
                                        ) {
                                            { model.cancelDownload(item.url) }
                                        } else {
                                            null
                                        },
                                        onDeleteClick = if (canDownload) {
                                            { model.deleteDownload(item.url) }
                                        } else {
                                            null
                                        },
                                        onClick = {
                                            if (selecting) {
                                                model.toggleSelection(item.url)
                                            } else {
                                                onItemClick(item)
                                            }
                                        },
                                        // Android's action mode — long-press selects
                                        // once the entry has a local row to mutate.
                                        onLongClick = if (state.local != null) {
                                            { model.toggleSelection(item.url) }
                                        } else {
                                            null
                                        },
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
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
            if (metaItems.isNotEmpty()) {
                Text(
                    text = metaItems.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = SECONDARY_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
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
) {
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary

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

@Composable
private fun ItemRow(
    item: RemoteItem,
    numberPrefix: String,
    done: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    bookmarked: Boolean = false,
    displayByNumber: Boolean = false,
    selected: Boolean = false,
    showPreview: Boolean = true,
    showSummary: Boolean = true,
    /** Android's ChapterDownloadIndicator — null hides it (shows/remote-only). */
    downloadState: DownloadState? = null,
    downloadProgress: Float = 0f,
    downloaded: Boolean = false,
    onDownloadClick: (() -> Unit)? = null,
    /** Tap while queued/downloading cancels — Android's indicator behavior. */
    onCancelClick: (() -> Unit)? = null,
    /** Tap while downloaded deletes the files — Android's indicator behavior. */
    onDeleteClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) {
                    // Android's action-mode row highlight.
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                },
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (showPreview && !item.previewUrl.isNullOrBlank()) {
            AsyncImage(
                model = item.previewUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(MaterialTheme.shapes.small),
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                // Android's displayMode flag — "Chapter 5" vs the source
                // title as the row's primary text.
                text = if (displayByNumber && item.number >= 0.0) {
                    "$numberPrefix ${formatItemNumber(item.number)}"
                } else {
                    item.name
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (done) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
            if (bookmarked) {
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            // Android's ChapterDownloadIndicator — icon state mirrors
            // queued / downloading (progress ring) / downloaded / error.
            when {
                downloadState == DownloadState.DOWNLOADING -> {
                    CircularProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(16.dp)
                            .then(
                                if (onCancelClick != null) {
                                    Modifier.clickable(onClick = onCancelClick)
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(16.dp)
                            .then(
                                if (onCancelClick != null) {
                                    Modifier.clickable(onClick = onCancelClick)
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
                        modifier = Modifier.padding(start = 8.dp).size(18.dp),
                    )
                }
                downloaded -> {
                    Icon(
                        imageVector = Icons.Filled.DownloadDone,
                        contentDescription = "Downloaded",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(18.dp)
                            .then(
                                if (onDeleteClick != null) {
                                    Modifier.clickable(onClick = onDeleteClick)
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
                onDownloadClick != null -> {
                    Icon(
                        imageVector = Icons.Outlined.FileDownload,
                        contentDescription = "Download",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(18.dp)
                            .clickable(onClick = onDownloadClick),
                    )
                }
            }
        }
        if (item.number >= 0.0 && !displayByNumber) {
            Text(
                text = "$numberPrefix ${formatItemNumber(item.number)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (done) 0.5f else 1f,
                ),
            )
        }
        if (showSummary && !item.summary.isNullOrBlank()) {
            Text(
                text = item.summary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (done) 0.5f else 1f,
                ),
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

/**
 * Android's swipe-reveal background — the action icon at the revealed edge
 * on primaryContainer, mirroring saket.swipe's action layout.
 */
@Composable
private fun SwipeActionBackground(
    action: ItemSwipeAction,
    /** True when the reveal is at the start edge (swipe right). */
    towardStart: Boolean,
) {
    val icon = when (action) {
        ItemSwipeAction.ToggleDone -> Icons.Filled.Visibility
        ItemSwipeAction.ToggleBookmark -> Icons.Filled.Bookmark
        ItemSwipeAction.ToggleFillermark -> Icons.Filled.Label
        ItemSwipeAction.Download -> Icons.Outlined.FileDownload
        ItemSwipeAction.Disabled -> Icons.Outlined.FileDownload
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = if (towardStart) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}
