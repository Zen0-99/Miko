package miko.ui.updates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
            !state.initialized -> LoadingScreen(modifier = Modifier.fillMaxSize())
            state.rows.isEmpty() -> EmptyScreen(
                stringRes = if (state.mode == UpdatesScreenModel.Mode.Updates) {
                    MR.strings.information_no_recent
                } else {
                    MR.strings.information_no_recent_manga
                },
                modifier = Modifier.fillMaxSize(),
            )
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                            onDownloadClick = { model.onDownloadClick(row) },
                            onToggleDone = { model.markItemDone(row.itemId, !row.done) },
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
}

@Composable
private fun UpdateRow(
    row: UpdatesScreenModel.Row.Update,
    carrier: BrowseCarrier,
    downloadState: DownloadState?,
    downloadProgress: Float,
    downloaded: Boolean,
    onDownloadClick: () -> Unit,
    onClick: () -> Unit,
    onCoverClick: () -> Unit,
    onToggleDone: () -> Unit,
) {
    // Read items dim like Android's read-state rows; trailing check toggles.
    CoverListItem(
        title = row.entryTitle,
        coverUrl = row.coverUrl,
        onClick = onClick,
        onLongClick = onCoverClick,
        subtitle = row.itemName,
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
        onLongClick = onCoverClick,
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
