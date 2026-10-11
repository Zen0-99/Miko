package miko.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tachiyomi.domain.collection.model.Collection
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Android's ChangeCollectionDialog — checkbox list of visible collections,
 * "Edit collections" manage shortcut, Cancel/OK confirmation returning the
 * selected ids. Single-entry selection only (no tri-state — that's for the
 * multi-select library variant).
 */
@Composable
fun ChangeCollectionSheet(
    collections: List<Collection>,
    initialSelection: Set<Long>,
    onDismissRequest: () -> Unit,
    onEditCollections: () -> Unit,
    onConfirm: (List<Long>) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp),
        ) {
            Text(
                text = stringResource(MR.strings.action_move_collection),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    start = 24.dp,
                    top = 8.dp,
                    end = 24.dp,
                    bottom = 12.dp,
                ),
            )

            if (collections.isEmpty()) {
                Text(
                    text = stringResource(MR.strings.information_empty_collection_dialog),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onEditCollections()
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_edit_collections))
                    }
                }
            } else {
                var selection by remember { mutableStateOf(initialSelection) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    collections.forEach { collection ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp)
                                .clickable {
                                    selection = if (collection.id in selection) {
                                        selection - collection.id
                                    } else {
                                        selection + collection.id
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = collection.id in selection,
                                onCheckedChange = {
                                    selection = if (collection.id in selection) {
                                        selection - collection.id
                                    } else {
                                        selection + collection.id
                                    }
                                },
                            )
                            Text(
                                text = collection.name,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onEditCollections()
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_edit_collections))
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onDismissRequest) {
                            Text(text = stringResource(MR.strings.action_cancel))
                        }
                        TextButton(onClick = { onConfirm(selection.toList()) }) {
                            Text(text = stringResource(MR.strings.action_ok))
                        }
                    }
                }
            }
        }
    }
}
