package eu.kanade.presentation.more.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import tachiyomi.domain.savedsearches.model.SavedSearch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Button
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun SavedSearchesDialog(
    onDismissRequest: () -> Unit,
    savedSearches: List<SavedSearch>,
    onSaveSearch: (String) -> Unit,
    onApplySearch: (SavedSearch) -> Unit,
    onDeleteSearch: (Long) -> Unit,
) {
    var showSaveField by remember { mutableStateOf(false) }
    var searchName by remember { mutableStateOf("") }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        LazyColumn {
            stickyHeader {
                Row(
                    modifier = Modifier
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(MR.strings.saved_searches),
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    if (!showSaveField) {
                        TextButton(onClick = { showSaveField = true }) {
                            Text(stringResource(MR.strings.action_save))
                        }
                    }
                }
            }

            if (showSaveField) {
                item {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = searchName,
                            onValueChange = { searchName = it },
                            label = { Text(stringResource(MR.strings.name)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        Button(
                            onClick = {
                                if (searchName.isNotBlank()) {
                                    onSaveSearch(searchName)
                                    showSaveField = false
                                    searchName = ""
                                }
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Text(stringResource(MR.strings.action_save))
                        }
                    }
                }
            }

            items(savedSearches) { savedSearch ->
                Row(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            onApplySearch(savedSearch)
                            onDismissRequest()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = savedSearch.name,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    IconButton(onClick = { onDeleteSearch(savedSearch.id) }) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(MR.strings.action_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (savedSearches.isEmpty() && !showSaveField) {
                item {
                    Text(
                        text = stringResource(MR.strings.no_saved_searches),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
