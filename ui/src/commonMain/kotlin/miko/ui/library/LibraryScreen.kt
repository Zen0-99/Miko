package miko.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import miko.ui.carrier.BrowseCarrier
import miko.ui.components.ChangeCollectionSheet
import miko.ui.components.CoverComfortableItem
import miko.ui.components.CoverGridItem
import miko.ui.components.CoverListItem
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Android's library compact grid — cover grid with EntryCompactGridItem
 * cells (title overlay + UnviewedBadge top-start) for the active carrier.
 * Long-press enters Android's selection mode: the top bar swaps to
 * count + select-all/inverse and a bottom action menu offers
 * collections / mark viewed / mark unviewed / delete.
 */
@Composable
fun LibraryScreen(
    model: LibraryScreenModel,
    carrierFlow: StateFlow<BrowseCarrier>,
    onCarrierChange: (BrowseCarrier) -> Unit,
    onEntryClick: (LibraryScreenModel.LibraryEntry) -> Unit,
    modifier: Modifier = Modifier,
    onManageCollections: () -> Unit = {},
) {
    val state by model.state.collectAsStateWithLifecycle()
    val carrier by carrierFlow.collectAsStateWithLifecycle()
    val selecting = state.selected.isNotEmpty()
    var collectionsOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }

    val onCellClick: (LibraryScreenModel.LibraryEntry) -> Unit = { entry ->
        if (selecting) model.toggleSelect(entry.id) else onEntryClick(entry)
    }

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

        // Android's library collection tabs — "All" + the visible
        // collections; hidden entirely when none exist.
        if (state.collections.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.selectedCollectionId == null,
                    onClick = { model.selectCollection(null) },
                    label = { Text(stringResource(MR.strings.all)) },
                )
                state.collections.forEach { collection ->
                    FilterChip(
                        selected = state.selectedCollectionId == collection.id,
                        onClick = { model.selectCollection(collection.id) },
                        label = { Text(collection.name) },
                    )
                }
            }
        }

        // Android's library pull-to-refresh — the per-carrier update job.
        val refreshing by model.refreshing.collectAsStateWithLifecycle()
        Box(modifier = Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = model::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    !state.initialized -> LoadingScreen(modifier = Modifier.fillMaxSize())
                    state.entries.isEmpty() -> EmptyScreen(
                        stringRes = MR.strings.information_empty_library,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Android's display_mode LIST — 56dp EntryListItem rows.
                    state.displayMode == LibraryDisplayMode.List -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                    ) {
                        items(state.entries, key = { it.id }) { entry ->
                            CoverListItem(
                                title = entry.title,
                                coverUrl = entry.coverUrl,
                                onClick = { onCellClick(entry) },
                                onLongClick = { model.toggleSelect(entry.id) },
                                isSelected = entry.id in state.selected,
                                trailing = if (entry.unreadCount > 0) {
                                    { Badge { Text("${entry.unreadCount}") } }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(state.entries, key = { it.id }) { entry ->
                            // ComfortableGrid puts the title below the cover;
                            // Compact/CoverOnly overlay it on a scrim.
                            if (state.displayMode == LibraryDisplayMode.ComfortableGrid) {
                                CoverComfortableItem(
                                    title = entry.title,
                                    coverUrl = entry.coverUrl,
                                    onClick = { onCellClick(entry) },
                                    onLongClick = { model.toggleSelect(entry.id) },
                                    isSelected = entry.id in state.selected,
                                    coverBadge = if (entry.unreadCount > 0) {
                                        { Badge { Text("${entry.unreadCount}") } }
                                    } else {
                                        null
                                    },
                                )
                            } else {
                                CoverGridItem(
                                    title = if (state.showTitle) entry.title else null,
                                    coverUrl = entry.coverUrl,
                                    onClick = { onCellClick(entry) },
                                    onLongClick = { model.toggleSelect(entry.id) },
                                    isSelected = entry.id in state.selected,
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

            LibrarySelectionMenu(
                visible = selecting,
                carrier = carrier,
                onChangeCollections = { collectionsOpen = true },
                onMarkViewed = { model.markSelectedDone(true) },
                onMarkUnviewed = { model.markSelectedDone(false) },
                onDelete = { deleteOpen = true },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (collectionsOpen) {
        ChangeCollectionSheet(
            collections = state.collections,
            initialSelection = emptySet(),
            onDismissRequest = { collectionsOpen = false },
            onEditCollections = {
                collectionsOpen = false
                onManageCollections()
            },
            onConfirm = { ids ->
                collectionsOpen = false
                model.setSelectedCollections(ids)
            },
        )
    }

    if (deleteOpen) {
        DeleteLibraryEntriesDialog(
            carrier = carrier,
            onDismiss = { deleteOpen = false },
            onConfirm = { fromLibrary, deleteDownloads ->
                deleteOpen = false
                model.removeSelected(fromLibrary, deleteDownloads)
            },
        )
    }
}

/**
 * Android's LibraryBottomActionMenu — Set collections, mark viewed,
 * mark unviewed, delete. Downloads stay hidden (Android passes null
 * for manga; shows aren't file-downloadable).
 */
@Composable
private fun LibrarySelectionMenu(
    visible: Boolean,
    carrier: BrowseCarrier,
    onChangeCollections: () -> Unit,
    onMarkViewed: () -> Unit,
    onMarkUnviewed: () -> Unit,
    onDelete: () -> Unit,
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
                bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp),
                bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp),
            ),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            ) {
                SelectionAction(
                    label = stringResource(MR.strings.action_move_collection),
                    icon = Icons.Outlined.Label,
                    onClick = onChangeCollections,
                )
                SelectionAction(
                    label = stringResource(
                        if (isShows) {
                            AYMR.strings.action_mark_as_seen
                        } else {
                            MR.strings.action_mark_as_read
                        },
                    ),
                    icon = Icons.Filled.Visibility,
                    onClick = onMarkViewed,
                )
                SelectionAction(
                    label = stringResource(
                        if (isShows) {
                            AYMR.strings.action_mark_as_unseen
                        } else {
                            MR.strings.action_mark_as_unread
                        },
                    ),
                    icon = Icons.Outlined.VisibilityOff,
                    onClick = onMarkUnviewed,
                )
                SelectionAction(
                    label = stringResource(MR.strings.action_delete),
                    icon = Icons.Outlined.Delete,
                    onClick = onDelete,
                )
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
        )
    }
}

/**
 * Android's DeleteLibraryEntryDialog — "Remove" with checkboxes for
 * from-library and downloaded items; confirm requires one checked.
 * Shows have no file downloads so the second checkbox hides there.
 */
@Composable
private fun DeleteLibraryEntriesDialog(
    carrier: BrowseCarrier,
    onDismiss: () -> Unit,
    onConfirm: (fromLibrary: Boolean, deleteDownloads: Boolean) -> Unit,
) {
    val isShows = carrier == BrowseCarrier.SHOWS
    var fromLibrary by remember { mutableStateOf(false) }
    var deleteDownloads by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.action_remove)) },
        text = {
            Column {
                LabeledCheckbox(
                    label = stringResource(
                        if (isShows) {
                            AYMR.strings.anime_from_library
                        } else {
                            // Generic "From library" — the AYMR variant names
                            // manga; books share the chapters wording like
                            // Android's isManga=true novel dialog.
                            MR.strings.manga_from_library
                        },
                    ),
                    checked = fromLibrary,
                    onCheckedChange = { fromLibrary = it },
                )
                if (!isShows) {
                    LabeledCheckbox(
                        label = stringResource(MR.strings.downloaded_chapters),
                        checked = deleteDownloads,
                        onCheckedChange = { deleteDownloads = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = fromLibrary || deleteDownloads,
                onClick = { onConfirm(fromLibrary, deleteDownloads) },
            ) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}
