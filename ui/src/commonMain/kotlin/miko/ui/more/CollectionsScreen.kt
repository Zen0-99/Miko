package miko.ui.more

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import tachiyomi.domain.collection.model.Collection
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Android's CollectionsTab — the manage screen behind More → Collections and
 * the details sheet's "Edit collections". Lists the active carrier's visible
 * collections; FAB creates, per-row overflow renames/deletes.
 */
@Composable
fun CollectionsScreen(
    model: CollectionsScreenModel,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsState()

    var createDialogOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Collection?>(null) }
    var deleteTarget by remember { mutableStateOf<Collection?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.collections.isEmpty()) {
                Text(
                    text = stringResource(MR.strings.information_empty_collection),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.collections, key = { it.id }) { collection ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp),
                        ) {
                            Text(
                                text = collection.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            var menuOpen by remember(collection.id) {
                                mutableStateOf(false)
                            }
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(
                                        imageVector = Icons.Outlined.MoreVert,
                                        contentDescription = null,
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false },
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    MR.strings.action_rename_collection,
                                                ),
                                            )
                                        },
                                        onClick = {
                                            menuOpen = false
                                            renameTarget = collection
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(stringResource(MR.strings.action_delete))
                                        },
                                        onClick = {
                                            menuOpen = false
                                            deleteTarget = collection
                                        },
                                    )
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { createDialogOpen = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(MR.strings.action_add_collection),
            )
        }
    }

    if (createDialogOpen) {
        CollectionNameDialog(
            title = stringResource(MR.strings.action_add_collection),
            initial = "",
            onDismiss = { createDialogOpen = false },
            onConfirm = {
                model.createCollection(it)
                createDialogOpen = false
            },
        )
    }

    renameTarget?.let { target ->
        CollectionNameDialog(
            title = stringResource(MR.strings.action_rename_collection),
            initial = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = {
                model.renameCollection(target, it)
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(MR.strings.action_delete)) },
            text = { Text(target.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        model.deleteCollection(target)
                        deleteTarget = null
                    },
                ) {
                    Text(stringResource(MR.strings.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}

/** Android's CollectionCreateDialog/CollectionRenameDialog — name field + OK. */
@Composable
private fun CollectionNameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(MR.strings.name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
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
