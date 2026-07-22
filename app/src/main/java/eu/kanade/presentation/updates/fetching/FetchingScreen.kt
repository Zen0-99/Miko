package eu.kanade.presentation.updates.fetching

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.tachiyomi.data.library.LibraryUpdateProgress
import eu.kanade.tachiyomi.ui.updates.fetching.FailedFetchGroup
import eu.kanade.tachiyomi.ui.updates.fetching.FailedFetchUi
import eu.kanade.tachiyomi.ui.updates.fetching.FetchingScreenModel
import eu.kanade.tachiyomi.ui.updates.fetching.FetchingState
import tachiyomi.domain.library.model.EntryKind
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun FetchingScreen(
    state: FetchingState,
    dialog: FetchingScreenModel.Dialog?,
    onMigrate: (FailedFetchUi) -> Unit,
    onDismissEntry: (Long) -> Unit,
    onDismissGroup: (String) -> Unit,
    onClearAll: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (dialog) {
        is FetchingScreenModel.Dialog.ClearAllConfirmation -> ClearAllDialog(
            onConfirm = onClearAll,
            onDismiss = { /* handled by screen model via setDialog(null) */ },
        )
        null -> {}
    }

    when (state) {
        FetchingState.Loading -> Box(Modifier.fillMaxSize())
        is FetchingState.Ready -> {
            val groups = remember(state.failedFetches) {
                state.failedFetches
                    .groupBy { it.reason }
                    .map { (reason, entries) -> FailedFetchGroup(reason, entries) }
                    .sortedByDescending { it.entries.size }
            }
            FetchingScreenContent(
                progress = state.progress,
                groups = groups,
                onMigrate = onMigrate,
                onDismissEntry = onDismissEntry,
                onDismissGroup = onDismissGroup,
                onPause = onPause,
                onResume = onResume,
                onCancel = onCancel,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun FetchingScreenContent(
    progress: LibraryUpdateProgress,
    groups: List<FailedFetchGroup>,
    onMigrate: (FailedFetchUi) -> Unit,
    onDismissEntry: (Long) -> Unit,
    onDismissGroup: (String) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        // Live progress section (only while running)
        if (progress is LibraryUpdateProgress.Running) {
            item(key = "progress") {
                ProgressCard(
                    progress = progress,
                    onPause = onPause,
                    onResume = onResume,
                    onCancel = onCancel,
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (groups.isEmpty()) {
            item(key = "empty") {
                EmptyState()
            }
        } else {
            groups.forEach { group ->
                item(key = "header-${group.reason}") {
                    GroupHeader(
                        reason = group.reason,
                        count = group.entries.size,
                        onClearGroup = { onDismissGroup(group.reason) },
                    )
                }
                items(items = group.entries, key = { it.id }) { entry ->
                    FailedEntryRow(
                        entry = entry,
                        onMigrate = { onMigrate(entry) },
                        onDismiss = { onDismissEntry(entry.id) },
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun ProgressCard(
    progress: LibraryUpdateProgress.Running,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(AYMR.strings.fetching_progress_title, progress.source),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row {
                    if (progress.isPaused) {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(AYMR.strings.action_resume))
                        }
                    } else {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Filled.Pause, contentDescription = stringResource(AYMR.strings.action_pause))
                        }
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(AYMR.strings.action_cancel))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val ratio = if (progress.totalEntries > 0) {
                progress.processedEntries.toFloat() / progress.totalEntries.toFloat()
            } else 0f
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${progress.processedEntries} / ${progress.totalEntries}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val current = progress.currentlyUpdating.firstOrNull()
            if (current != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = current.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (progress.failedSoFar.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(AYMR.strings.fetching_failed_so_far, progress.failedSoFar.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(reason: String, count: Int, onClearGroup: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = reason,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onClearGroup) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(AYMR.strings.action_clear_group))
        }
    }
}

@Composable
private fun FailedEntryRow(
    entry: FailedFetchUi,
    onMigrate: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onMigrate() },
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ItemCover.Book(
                data = entry.cover,
                modifier = Modifier.size(48.dp, 72.dp),
                contentDescription = entry.title,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.sourceName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onMigrate) {
                Icon(
                    Icons.Outlined.SwapHoriz,
                    contentDescription = stringResource(AYMR.strings.action_migrate_entry),
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(AYMR.strings.action_dismiss))
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(AYMR.strings.failed_fetches_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ClearAllDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(AYMR.strings.action_clear_failed_fetches)) },
        text = { Text(stringResource(AYMR.strings.clear_failed_fetches_confirmation)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(AYMR.strings.action_clear))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(AYMR.strings.action_cancel))
            }
        },
    )
}
