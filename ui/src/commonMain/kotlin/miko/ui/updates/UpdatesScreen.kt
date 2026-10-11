package miko.ui.updates

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import miko.ui.carrier.BrowseCarrier
import miko.ui.components.CoverListItem
import miko.ui.download.DownloadManager
import miko.ui.download.DownloadState
import miko.ui.extensions.ExtensionHeader
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.secondaryItemAlpha

/**
 * Updates tab — Android's UpdatesTab with History folded in as a sub-tab.
 * Update rows are the newly-fetched chapters/episodes of library entries
 * (grouped under date headers); History rows are the read/watch log. Row
 * click continues at that item in the matching reader/player; cover click
 * opens the entry's details.
 */
@Composable
fun UpdatesScreen(
    model: UpdatesScreenModel,
    carrierFlow: StateFlow<BrowseCarrier>,
    onCarrierChange: (BrowseCarrier) -> Unit,
    onOpenItem: (sourceId: Long, entryUrl: String, itemUrl: String) -> Unit,
    onOpenEntry: (sourceId: Long, entryUrl: String, entryTitle: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val carrier by carrierFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val downloadStates by DownloadManager.states.collectAsStateWithLifecycle()
    val downloadProgress by DownloadManager.progress.collectAsStateWithLifecycle()
    val downloadedDirs by model.downloadedDirs.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        // Carrier + sub-tab selectors — same chip row idiom as Sources/Library.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                BrowseCarrier.SHOWS to AYMR.strings.carrier_shows,
                BrowseCarrier.MANGA to AYMR.strings.carrier_manga,
                BrowseCarrier.BOOKS to AYMR.strings.carrier_books,
            ).forEach { (option, labelRes) ->
                FilterChip(
                    selected = carrier == option,
                    onClick = { onCarrierChange(option) },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.mode == UpdatesScreenModel.Mode.Updates,
                onClick = { model.setMode(UpdatesScreenModel.Mode.Updates) },
                label = { Text(stringResource(MR.strings.label_recent_updates)) },
            )
            FilterChip(
                selected = state.mode == UpdatesScreenModel.Mode.History,
                onClick = { model.setMode(UpdatesScreenModel.Mode.History) },
                label = { Text(stringResource(MR.strings.history)) },
            )
            FilterChip(
                selected = state.mode == UpdatesScreenModel.Mode.Upcoming,
                onClick = { model.setMode(UpdatesScreenModel.Mode.Upcoming) },
                label = { Text(stringResource(MR.strings.label_upcoming)) },
            )
            // Manual refresh — Android's updates toolbar action.
            val refreshing by model.refreshing.collectAsStateWithLifecycle()
            IconButton(
                onClick = { model.refresh() },
                enabled = !refreshing,
            ) {
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = stringResource(MR.strings.action_update_library),
                    )
                }
            }
            // Android's History-toolbar wipe — History mode only.
            if (state.mode == UpdatesScreenModel.Mode.History) {
                var wipeOpen by remember { mutableStateOf(false) }
                IconButton(onClick = { wipeOpen = true }) {
                    Icon(
                        Icons.Outlined.DeleteSweep,
                        contentDescription = stringResource(
                            MR.strings.action_remove_everything,
                        ),
                    )
                }
                if (wipeOpen) {
                    AlertDialog(
                        title = {
                            Text(
                                stringResource(MR.strings.action_remove_everything),
                            )
                        },
                        text = {
                            Text(
                                stringResource(MR.strings.clear_history_confirmation),
                            )
                        },
                        onDismissRequest = { wipeOpen = false },
                        confirmButton = {
                            TextButton(onClick = {
                                wipeOpen = false
                                model.clearHistory()
                            }) {
                                Text(stringResource(MR.strings.action_ok))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { wipeOpen = false }) {
                                Text(stringResource(MR.strings.action_cancel))
                            }
                        },
                    )
                }
            }
        }

        when {
            !state.initialized -> LoadingScreen(modifier = Modifier.weight(1f))
            state.rows.isEmpty() -> EmptyScreen(
                stringRes = if (state.mode == UpdatesScreenModel.Mode.Updates) {
                    MR.strings.information_no_recent
                } else {
                    MR.strings.information_no_recent_manga
                },
                modifier = Modifier.weight(1f),
            )
            else -> {
                val listState = rememberLazyListState()
                LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                    // Android's Calendar tab — month grid over the list;
                    // day taps scroll to that date's header group.
                    if (state.mode == UpdatesScreenModel.Mode.Upcoming) {
                        item(key = "upcoming-calendar") {
                            UpcomingCalendar(
                                monthStart = state.calendarMonthStart,
                                events = state.events,
                                onMonthChange = model::setCalendarMonth,
                                onDayClick = { date ->
                                    val index = state.rows.indexOfFirst {
                                        it is UpdatesScreenModel.Row.Header &&
                                            it.key == "u$date"
                                    }
                                    if (index >= 0) {
                                        scope.launch {
                                            // +1 accounts for the calendar item itself.
                                            listState.animateScrollToItem(index + 1)
                                        }
                                    }
                                },
                            )
                        }
                    }
                    items(
                        state.rows,
                        key = { row ->
                            when (row) {
                                is UpdatesScreenModel.Row.Header -> "h-${row.key}"
                                is UpdatesScreenModel.Row.Update -> "u-${row.itemId}"
                                is UpdatesScreenModel.Row.History -> "hist-${row.id}"
                                is UpdatesScreenModel.Row.Upcoming -> "up-${row.entryId}"
                            }
                        },
                    ) { row ->
                        when (row) {
                            is UpdatesScreenModel.Row.Header -> ExtensionHeader(row.label)
                            is UpdatesScreenModel.Row.Update -> UpdateRow(
                                row = row,
                                carrier = carrier,
                                downloadState = downloadStates[row.itemUrl],
                                downloadProgress = downloadProgress[row.itemUrl] ?: 0f,
                                downloaded = model.itemDirPath(row) in downloadedDirs,
                                selected = row.itemId in state.selected,
                                onDownloadClick = { model.onDownloadClick(row) },
                                onToggleDone = { model.markItemDone(row.itemId, !row.done) },
                                onClick = {
                                    // Selection mode eats the row click —
                                    // Android toggles instead of opening.
                                    if (state.selectionMode) {
                                        model.toggleSelection(row)
                                    } else {
                                        scope.launch {
                                            model.resolveTarget(row.entryId, row.itemId)
                                                ?.let { t ->
                                                    onOpenItem(t.sourceId, t.entryUrl, t.itemUrl)
                                                }
                                        }
                                    }
                                },
                                onLongClick = { model.toggleSelection(row) },
                                // Android gates the cover hit target on
                                // selection mode — cover taps toggle too.
                                onCoverClick = {
                                    scope.launch {
                                        model.resolveTarget(row.entryId, row.itemId)
                                            ?.let { t ->
                                                onOpenEntry(t.sourceId, t.entryUrl, row.entryTitle)
                                            }
                                    }
                                    Unit
                                }.takeIf { !state.selectionMode },
                            )
                            is UpdatesScreenModel.Row.History -> HistoryRow(
                                row = row,
                                carrier = carrier,
                                onDelete = { all -> model.removeHistory(row, all) },
                                onClick = {
                                    scope.launch {
                                        model.resolveTarget(row.entryId, row.itemId)
                                            ?.let { t ->
                                                onOpenItem(t.sourceId, t.entryUrl, t.itemUrl)
                                            }
                                    }
                                },
                                onCoverClick = {
                                    scope.launch {
                                        model.resolveTarget(row.entryId, row.itemId)
                                            ?.let { t ->
                                                onOpenEntry(t.sourceId, t.entryUrl, row.entryTitle)
                                            }
                                    }
                                },
                            )
                            is UpdatesScreenModel.Row.Upcoming -> UpcomingRow(
                                row = row,
                                onClick = {
                                    onOpenEntry(row.sourceId, row.entryUrl, row.entryTitle)
                                },
                            )
                        }
                    }
                }
            }
        }

        // Android's updates action-mode bottom bar — long-press selects;
        // actions gate on the selection's bookmark/read/download states.
        val selectedRows = state.rows.filterIsInstance<UpdatesScreenModel.Row.Update>()
            .filter { it.itemId in state.selected }
        UpdatesActionMenu(
            visible = state.selectionMode,
            carrier = carrier,
            anyUnbookmarked = selectedRows.any { !it.bookmarked },
            allBookmarked = selectedRows.isNotEmpty() && selectedRows.all { it.bookmarked },
            anyUndone = selectedRows.any { !it.done },
            anyDone = selectedRows.any { it.done },
            anyUndownloaded = selectedRows.any {
                model.itemDirPath(it) !in downloadedDirs
            },
            anyDownloaded = selectedRows.any {
                model.itemDirPath(it) in downloadedDirs
            },
            onBookmark = { model.bookmarkSelected(true) },
            onUnbookmark = { model.bookmarkSelected(false) },
            onMarkDone = { model.markSelectedDone(true) },
            onMarkUndone = { model.markSelectedDone(false) },
            onDownload = model::downloadSelected,
            onDeleteDownloads = model::deleteSelectedDownloads,
        )
    }
}

/**
 * Android's updates action-mode bar — the icon+label column idiom from
 * SelectionActionMenu; actions appear only when the selection supports
 * them (bookmark when any unbookmarked, download while any undownloaded).
 */
@Composable
private fun UpdatesActionMenu(
    visible: Boolean,
    carrier: BrowseCarrier,
    anyUnbookmarked: Boolean,
    allBookmarked: Boolean,
    anyUndone: Boolean,
    anyDone: Boolean,
    anyUndownloaded: Boolean,
    anyDownloaded: Boolean,
    onBookmark: () -> Unit,
    onUnbookmark: () -> Unit,
    onMarkDone: () -> Unit,
    onMarkUndone: () -> Unit,
    onDownload: () -> Unit,
    onDeleteDownloads: () -> Unit,
) {
    val isShows = carrier == BrowseCarrier.SHOWS
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(expandFrom = Alignment.Bottom),
        exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            ) {
                if (anyUnbookmarked) {
                    UpdatesAction(
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
                    UpdatesAction(
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
                if (anyUndone) {
                    UpdatesAction(
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
                    UpdatesAction(
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
                if (!isShows && anyUndownloaded) {
                    UpdatesAction(
                        label = stringResource(MR.strings.action_download),
                        icon = Icons.Outlined.Download,
                        onClick = onDownload,
                    )
                }
                if (!isShows && anyDownloaded) {
                    UpdatesAction(
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
private fun RowScope.UpdatesAction(
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
 * Android's upcoming calendar — month header with ‹ › arrows and a
 * 7-column day grid (Sunday-first). Days carry up to three event dots;
 * today's cell gets a ring and past days render dimmed.
 */
@Composable
private fun UpcomingCalendar(
    monthStart: LocalDate,
    events: Map<LocalDate, Int>,
    onMonthChange: (LocalDate) -> Unit,
    onDayClick: (LocalDate) -> Unit,
) {
    val today = remember {
        Instant.fromEpochMilliseconds(kotlin.time.Clock.System.now().toEpochMilliseconds())
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = monthLabel(monthStart),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onMonthChange(monthStart.shiftMonths(-1)) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                )
            }
            IconButton(onClick = { onMonthChange(monthStart.shiftMonths(1)) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            calendarWeekdayInitials.forEach { initial ->
                Text(
                    text = initial,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val daysInMonth = daysInMonth(monthStart.year, monthStart.month)
        // Sunday-start grid — DayOfWeek ordinals are Mon=0..Sun=6.
        val leading = (monthStart.dayOfWeek.ordinal + 1) % 7
        val cells = List(leading) { 0 } + (1..daysInMonth).toList()
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    if (day == 0) {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = LocalDate(monthStart.year, monthStart.month, day)
                        CalendarDayCell(
                            day = day,
                            events = events[date] ?: 0,
                            isToday = date == today,
                            isPast = date < today,
                            onClick = { onDayClick(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                // Partial last week — keep the grid's 7-column shape.
                repeat(7 - week.size) {
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }
}

private val calendarWeekdayInitials = listOf("S", "M", "T", "W", "T", "F", "S")

private fun daysInMonth(year: Int, month: Month): Int = when (month) {
    Month.FEBRUARY -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    Month.APRIL, Month.JUNE, Month.SEPTEMBER, Month.NOVEMBER -> 30
    else -> 31
}

/** One calendar cell — day number over up to three event dots. */
@Composable
private fun CalendarDayCell(
    day: Int,
    events: Int,
    isToday: Boolean,
    isPast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .then(
                if (isToday) {
                    Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.onBackground,
                        CircleShape,
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground.copy(
                    alpha = if (isPast) 0.4f else 1f,
                ),
            )
            Row {
                repeat(events.coerceAtMost(3)) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateRow(
    row: UpdatesScreenModel.Row.Update,
    carrier: BrowseCarrier,
    downloadState: DownloadState?,
    downloadProgress: Float,
    downloaded: Boolean,
    selected: Boolean,
    onDownloadClick: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCoverClick: (() -> Unit)?,
    onToggleDone: () -> Unit,
) {
    // Read items dim like Android's read-state rows; trailing check toggles.
    CoverListItem(
        title = row.entryTitle,
        coverUrl = row.coverUrl,
        onClick = onClick,
        onLongClick = onLongClick,
        subtitle = row.itemName,
        isSelected = selected,
        onCoverClick = onCoverClick,
        modifier = if (row.done) Modifier.secondaryItemAlpha() else Modifier,
        trailing = {
            if (carrier != BrowseCarrier.SHOWS) {
                // Android's ChapterDownloadIndicator — cycles
                // enqueue → cancel → delete on tap.
                when {
                    downloadState == DownloadState.DOWNLOADING -> {
                        CircularProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .size(20.dp)
                                .clickable(onClick = onDownloadClick),
                            strokeWidth = 2.dp,
                        )
                    }
                    downloadState == DownloadState.QUEUED -> {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = stringResource(
                                AYMR.strings.download_queue_status_queued,
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable(onClick = onDownloadClick),
                        )
                    }
                    downloadState == DownloadState.ERROR -> {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = stringResource(
                                AYMR.strings.download_queue_status_failed,
                            ),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable(onClick = onDownloadClick),
                        )
                    }
                    downloaded -> {
                        Icon(
                            imageVector = Icons.Filled.DownloadDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable(onClick = onDownloadClick),
                        )
                    }
                    else -> {
                        IconButton(onClick = onDownloadClick) {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = stringResource(
                                    MR.strings.action_download,
                                ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                IconButton(onClick = onToggleDone) {
                    Icon(
                        imageVector = if (row.done) {
                            Icons.Filled.CheckCircle
                        } else {
                            Icons.Outlined.CheckCircle
                        },
                        contentDescription = stringResource(MR.strings.action_mark_as_read),
                        tint = if (row.done) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun UpcomingRow(
    row: UpdatesScreenModel.Row.Upcoming,
    onClick: () -> Unit,
) {
    CoverListItem(
        title = row.entryTitle,
        coverUrl = row.coverUrl,
        onClick = onClick,
        subtitle = dateLabel(
            kotlin.time.Instant.fromEpochMilliseconds(row.nextUpdate)
                .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date,
        ),
    )
}

@Composable
private fun HistoryRow(
    row: UpdatesScreenModel.Row.History,
    carrier: BrowseCarrier,
    onClick: () -> Unit,
    onCoverClick: () -> Unit,
    onDelete: (all: Boolean) -> Unit,
) {
    val subtitle = buildString {
        append(row.itemName)
        row.readAt?.let { append("  ·  ${timeLabel(it)}") }
    }
    var deleteOpen by remember { mutableStateOf(false) }
    CoverListItem(
        title = row.entryTitle,
        coverUrl = row.coverUrl,
        onClick = onClick,
        onCoverClick = onCoverClick,
        subtitle = subtitle,
        trailing = {
            IconButton(onClick = { deleteOpen = true }) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(MR.strings.action_remove),
                )
            }
        },
    )
    // Android's HistoryDeleteDialog — row remove, checkbox resets the
    // whole entry's history.
    if (deleteOpen) {
        var removeAll by remember { mutableStateOf(false) }
        AlertDialog(
            title = { Text(stringResource(MR.strings.action_remove)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(
                            if (carrier == BrowseCarrier.SHOWS) {
                                AYMR.strings
                                    .dialog_with_checkbox_remove_description_anime
                            } else {
                                MR.strings.dialog_with_checkbox_remove_description
                            },
                        ),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { removeAll = !removeAll },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = removeAll,
                            onCheckedChange = { removeAll = it },
                        )
                        Text(
                            text = stringResource(
                                when (carrier) {
                                    BrowseCarrier.SHOWS ->
                                        AYMR.strings
                                            .dialog_with_checkbox_reset_anime
                                    BrowseCarrier.MANGA ->
                                        AYMR.strings
                                            .dialog_with_checkbox_reset
                                    BrowseCarrier.BOOKS ->
                                        MR.strings
                                            .dialog_with_checkbox_reset
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            onDismissRequest = { deleteOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    deleteOpen = false
                    onDelete(removeAll)
                }) {
                    Text(stringResource(MR.strings.action_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteOpen = false }) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}

private fun timeLabel(epochMs: Long): String {
    val instant = kotlin.time.Instant.fromEpochMilliseconds(epochMs)
    val ldt = instant.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    val today = kotlin.time.Instant.fromEpochMilliseconds(
        kotlin.time.Clock.System.now().toEpochMilliseconds(),
    ).toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
    // padStart, not String.format — the latter is JVM-only on K/N.
    val time = ldt.hour.toString().padStart(2, '0') + ":" +
        ldt.minute.toString().padStart(2, '0')
    return if (ldt.date == today) time else dateLabel(ldt.date) + " " + time
}
