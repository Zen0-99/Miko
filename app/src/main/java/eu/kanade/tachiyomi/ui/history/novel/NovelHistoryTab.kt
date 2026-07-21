package eu.kanade.tachiyomi.ui.history.novel

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.history.HistoryDeleteAllDialog
import eu.kanade.presentation.history.HistoryDeleteDialog
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.format.DateTimeFormatter
import java.util.Date

val resumeLastNovelChapterReadEvent = Channel<Unit>()

@Composable
fun Screen.novelHistoryTab(
    context: Context,
    fromMore: Boolean,
): TabContent {
    val snackbarHostState = SnackbarHostState()

    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { NovelHistoryScreenModel() }
    val state by screenModel.state.collectAsState()
    val searchQuery by screenModel.query.collectAsState()

    suspend fun openChapter(context: Context, novelId: Long, chapterId: Long) {
        navigator.push(NovelReaderScreen(novelId, chapterId))
    }

    val scope = rememberCoroutineScope()

    return TabContent(
        titleRes = AYMR.strings.label_history,
        searchEnabled = true,
        content = { contentPadding, _ ->
            NovelHistoryScreen(
                state = state,
                searchQuery = searchQuery,
                contentPadding = contentPadding,
                onClickCover = { navigator.push(NovelScreen(it)) },
                onClickResume = { history ->
                    screenModel.getNextChapterForNovel(history.novelId, history.chapterId)
                },
                onDialogChange = screenModel::setDialog,
            )

            val onDismissRequest = { screenModel.setDialog(null) }
            when (val dialog = state.dialog) {
                is NovelHistoryScreenModel.Dialog.Delete -> {
                    HistoryDeleteDialog(
                        onDismissRequest = onDismissRequest,
                        onDelete = { all ->
                            if (all) {
                                screenModel.removeAllFromHistory(dialog.history.novelId)
                            } else {
                                screenModel.removeFromHistory(dialog.history)
                            }
                        },
                        isManga = false,
                    )
                }
                is NovelHistoryScreenModel.Dialog.DeleteAll -> {
                    HistoryDeleteAllDialog(
                        onDismissRequest = onDismissRequest,
                        onDelete = screenModel::removeAllHistory,
                    )
                }
                null -> {}
            }

            LaunchedEffect(Unit) {
                screenModel.events.collectLatest { e ->
                    when (e) {
                        NovelHistoryScreenModel.Event.InternalError ->
                            snackbarHostState.showSnackbar(context.stringResource(MR.strings.internal_error))
                        NovelHistoryScreenModel.Event.HistoryCleared ->
                            snackbarHostState.showSnackbar(context.stringResource(MR.strings.clear_history_completed))
                        is NovelHistoryScreenModel.Event.OpenChapter -> {
                            val chapter = e.chapter
                            if (chapter != null) {
                                openChapter(context, chapter.novelId, chapter.id)
                            } else {
                                snackbarHostState.showSnackbar(context.stringResource(MR.strings.no_next_chapter))
                            }
                        }
                    }
                }
            }

            LaunchedEffect(Unit) {
                resumeLastNovelChapterReadEvent.receiveAsFlow().collectLatest {
                    val chapter = screenModel.getNextChapter()
                    if (chapter != null) {
                        openChapter(context, chapter.novelId, chapter.id)
                    }
                }
            }
        },
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.pref_clear_history),
                icon = Icons.Outlined.DeleteSweep,
                onClick = { screenModel.setDialog(NovelHistoryScreenModel.Dialog.DeleteAll) },
            ),
        ),
        navigateUp = null,
    )
}

@Composable
private fun NovelHistoryScreen(
    state: NovelHistoryScreenModel.State,
    searchQuery: String?,
    contentPadding: PaddingValues,
    onClickCover: (Long) -> Unit,
    onClickResume: (tachiyomi.domain.history.novel.model.NovelHistoryWithRelations) -> Unit,
    onDialogChange: (NovelHistoryScreenModel.Dialog?) -> Unit,
) {
    val list = state.list
    if (list == null) {
        Box(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text("Loading...")
        }
        return
    }

    if (list.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No reading history",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        items(list) { item ->
            when (item) {
                is NovelHistoryUiModel.Header -> {
                    Text(
                        text = item.date.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                is NovelHistoryUiModel.Item -> {
                    NovelHistoryItem(
                        item = item.item,
                        onClickCover = onClickCover,
                        onClickResume = onClickResume,
                        onDialogChange = onDialogChange,
                    )
                }
                is NovelHistoryUiModel.Batch -> {
                    NovelHistoryBatchItem(
                        batch = item,
                        onClickCover = onClickCover,
                    )
                }
            }
        }
    }
}

@Composable
private fun NovelHistoryItem(
    item: tachiyomi.domain.history.novel.model.NovelHistoryWithRelations,
    onClickCover: (Long) -> Unit,
    onClickResume: (tachiyomi.domain.history.novel.model.NovelHistoryWithRelations) -> Unit,
    onDialogChange: (NovelHistoryScreenModel.Dialog?) -> Unit,
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("h:mm a") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Chapter ${item.chapterNumber}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.readAt?.let { readAt ->
                Text(
                    text = "Read at ${dateFormatter.format(readAt.toInstant().atZone(java.time.ZoneId.systemDefault()))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = { onClickResume(item) }) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Resume",
            )
        }
        IconButton(onClick = { onDialogChange(NovelHistoryScreenModel.Dialog.Delete(item)) }) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete",
            )
        }
    }
}

@Composable
private fun NovelHistoryBatchItem(
    batch: NovelHistoryUiModel.Batch,
    onClickCover: (Long) -> Unit,
) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val zone = remember { java.time.ZoneId.systemDefault() }
    val firstTime = remember(batch.firstReadAt) {
        timeFormatter.format(batch.firstReadAt.toInstant().atZone(zone))
    }
    val lastTime = remember(batch.lastReadAt) {
        timeFormatter.format(batch.lastReadAt.toInstant().atZone(zone))
    }
    val chapterRange = remember(batch.firstChapter, batch.lastChapter) {
        if (batch.firstChapter == batch.lastChapter) {
            "Chapter ${batch.firstChapter}"
        } else {
            "Chapter ${batch.firstChapter} - ${batch.lastChapter}"
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = batch.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = chapterRange,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$firstTime - $lastTime",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = { onClickCover(batch.novelId) }) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Open",
            )
        }
    }
}
