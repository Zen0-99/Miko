package miko.ui.download

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import miko.ui.extensions.ExtensionHeader
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import kotlin.math.roundToInt

/**
 * Android's DownloadQueue screen — pending and failed item downloads,
 * grouped per entry like upstream's header/sub-item list. Each row cancels
 * individually; the screen's "clear" action lives on the shared top bar.
 */
@Composable
fun DownloadQueueScreen(
    modifier: Modifier = Modifier,
) {
    val rows by DownloadManager.queueRows.collectAsStateWithLifecycle()
    val states by DownloadManager.states.collectAsStateWithLifecycle()
    val progress by DownloadManager.progress.collectAsStateWithLifecycle()

    if (rows.isEmpty()) {
        EmptyScreen(
            stringRes = MR.strings.information_no_downloads,
            modifier = modifier,
        )
        return
    }

    val groups = rows.groupBy { Triple(it.carrier, it.sourceName, it.entryTitle) }
    LazyColumn(modifier = modifier.fillMaxSize()) {
        groups.forEach { (_, groupRows) ->
            item(key = "h:${groupRows.first().entryTitle}") {
                ExtensionHeader(groupRows.first().entryTitle)
            }
            items(groupRows, key = { it.key }) { row ->
                QueueRowItem(
                    row = row,
                    state = states[row.key],
                    progress = progress[row.key],
                    onCancel = { DownloadManager.cancel(row.key) },
                )
            }
        }
    }
}

@Composable
private fun QueueRowItem(
    row: QueueRow,
    state: DownloadState?,
    progress: Float?,
    onCancel: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = if (state == DownloadState.ERROR) {
                Icons.Outlined.ErrorOutline
            } else {
                Icons.Outlined.Download
            },
            contentDescription = null,
            tint = if (state == DownloadState.ERROR) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f),
        ) {
            Text(
                text = row.itemName,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = when (state) {
                    DownloadState.DOWNLOADING -> stringResource(
                        AYMR.strings.download_queue_status_downloading,
                    ) + (progress?.let { " • ${(it * 100).roundToInt()}%" } ?: "")
                    DownloadState.ERROR -> stringResource(AYMR.strings.download_queue_status_failed)
                    else -> stringResource(AYMR.strings.download_queue_status_queued)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state == DownloadState.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { progress ?: 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
        }
        IconButton(onClick = onCancel) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(MR.strings.action_cancel),
            )
        }
    }
}
