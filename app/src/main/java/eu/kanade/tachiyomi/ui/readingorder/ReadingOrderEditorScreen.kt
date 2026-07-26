package eu.kanade.tachiyomi.ui.readingorder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.readingorder.model.ReadingOrderNode
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

class ReadingOrderEditorScreen(
    private val orderId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { ReadingOrderEditorScreenModel(orderId) }
        val state by screenModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        Scaffold(
            topBar = {
                AppBar(
                    title = state.order?.name ?: stringResource(AYMR.strings.reading_order),
                    navigateUp = { navigator.pop() },
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(AYMR.strings.reading_order_add_manga)) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = { screenModel.showAddMangaDialog() },
                )
            },
        ) { padding ->
            if (state.nodes.isEmpty() && !state.isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(AYMR.strings.reading_order_no_entries),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    items(state.nodes, key = { it.id }) { node ->
                        val manga = state.mangaMap[node.mangaId]
                        val progress = state.progress[node.mangaId]
                        val isCompleted = progress?.completed ?: false
                        val isLocked = isNodeLocked(node, state.edges, state.progress)

                        ReadingOrderEntryRow(
                            title = manga?.title ?: "Unknown",
                            isCompleted = isCompleted,
                            isLocked = isLocked,
                            onToggleCompleted = { screenModel.toggleCompleted(node.mangaId) },
                            onRemove = { screenModel.removeManga(node.mangaId) },
                        )
                    }
                }
            }
        }

        when (state.dialog) {
            is ReadingOrderEditorScreenModel.Dialog.AddManga -> AddMangaDialog(
                libraryManga = state.libraryManga,
                existingMangaIds = state.nodes.map { it.mangaId }.toSet(),
                onAdd = { screenModel.addManga(it) },
                onDismiss = { screenModel.closeDialog() },
            )
            null -> {}
        }
    }

    private fun isNodeLocked(
        node: ReadingOrderNode,
        edges: List<tachiyomi.domain.readingorder.model.ReadingOrderEdge>,
        progress: Map<Long, tachiyomi.domain.readingorder.model.ReadingOrderProgress>,
    ): Boolean {
        val prerequisites = edges.filter { it.toMangaId == node.mangaId }.map { it.fromMangaId }
        if (prerequisites.isEmpty()) return false
        return prerequisites.any { prereqId ->
            val p = progress[prereqId]
            p == null || !p.completed
        }
    }
}

@Composable
private fun ReadingOrderEntryRow(
    title: String,
    isCompleted: Boolean,
    isLocked: Boolean,
    onToggleCompleted: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleCompleted) {
            Icon(
                imageVector = if (isCompleted) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (isCompleted) {
                    stringResource(AYMR.strings.reading_order_entry_completed)
                } else {
                    stringResource(AYMR.strings.reading_order_mark_completed)
                },
                tint = if (isCompleted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (isLocked) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = stringResource(AYMR.strings.reading_order_entry_locked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(AYMR.strings.reading_order_remove_manga),
            )
        }
    }
}

@Composable
private fun AddMangaDialog(
    libraryManga: List<Manga>,
    existingMangaIds: Set<Long>,
    onAdd: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(AYMR.strings.reading_order_add_manga)) },
        text = {
            Column {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search library...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    val filtered = libraryManga.filter {
                        it.id !in existingMangaIds &&
                            (search.isBlank() || it.title.contains(search, ignoreCase = true))
                    }
                    items(filtered, key = { it.id }) { manga ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAdd(manga.id) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = manga.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(tachiyomi.i18n.MR.strings.action_cancel))
            }
        },
    )
}
