package miko.ui.updates

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import miko.ui.carrier.BrowseCarrier
import miko.ui.components.CoverListItem
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
                        }
                    },
                ) { row ->
                    when (row) {
                        is UpdatesScreenModel.Row.Header -> ExtensionHeader(row.label)
                        is UpdatesScreenModel.Row.Update -> UpdateRow(
                            row = row,
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
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateRow(
    row: UpdatesScreenModel.Row.Update,
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
        },
    )
}

@Composable
private fun HistoryRow(
    row: UpdatesScreenModel.Row.History,
    onClick: () -> Unit,
    onCoverClick: () -> Unit,
) {
    val subtitle = buildString {
        append(row.itemName)
        row.readAt?.let { append("  ·  ${timeLabel(it)}") }
    }
    CoverListItem(
        title = row.entryTitle,
        coverUrl = row.coverUrl,
        onClick = onClick,
        onLongClick = onCoverClick,
        subtitle = subtitle,
    )
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
